package pe.nanamochi.banchus.controllers;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.nanamochi.banchus.entities.*;
import pe.nanamochi.banchus.entities.db.*;
import pe.nanamochi.banchus.services.*;
import pe.nanamochi.banchus.utils.OsuApi;

@RestController
@RequestMapping("/web")
public class LeaderboardController {
  private static final Logger logger = LoggerFactory.getLogger(LeaderboardController.class);

  @Autowired private UserService userService;
  @Autowired private SessionService sessionService;
  @Autowired private BeatmapService beatmapService;
  @Autowired private BeatmapsetService beatmapsetService;
  @Autowired private ScoreService scoreService;
  @Autowired private OsuApi osuApi;

  @GetMapping("/osu-osz2-getscores.php")
  public String getScores(
      @RequestParam(name = "us") String username,
      @RequestParam(name = "ha") String passwordMd5,
      @RequestParam(name = "s") Boolean skipScores,
      @RequestParam(name = "vv") Integer requestVersion,
      @RequestParam(name = "v") Integer leaderboardType,
      @RequestParam(name = "c") String beatmapMd5,
      @RequestParam(name = "f") String beatmapFilename,
      @RequestParam(name = "m") Integer gamemode,
      @RequestParam(name = "i") Integer beatmapSetId,
      @RequestParam(name = "mods") Integer modsBitmask,
      @RequestParam(name = "h") String mapPackageHash,
      @RequestParam(name = "a") Boolean aqnFilesFound) {
    logger.info("Received request for leaderboard scores");

    // This is a quirk of the osu! client, where it adjusts this value only after it sends the
    // packet to the server; so we need to adjust
    List<Mods> mods =
        Mods.filterInvalidModCombinations(Mods.fromBitmask(modsBitmask), Mode.fromValue(gamemode));
    Mode mode = Mode.fromValue(gamemode);

    // TODO: Fix the responses in the case of and error
    User user = userService.login(username, passwordMd5);
    if (user == null) {
      logger.warn("Login failed for user: {}", username);
      return "";
    }

    Session session = sessionService.getPrimarySessionByUsername(user.getUsername());
    if (session == null) {
      logger.warn("No session found for user: {}", username);
      return "";
    }

    // TODO: update user stats if they have changed

    // TODO: fetch the beatmap with this md5
    Beatmap beatmap = beatmapService.findByMd5(beatmapMd5);

    if (beatmap == null) {
      logger.info("Beatmap not found locally, fetching from osu! API: {}", beatmapMd5);

      pe.nanamochi.banchus.entities.osuapi.Beatmap osuApiBeatmap = osuApi.getBeatmap(beatmapMd5);
      if (osuApiBeatmap == null) {
        logger.warn("Beatmap not found in osu! API: {}", beatmapMd5);
        return ""; // TODO: Beatmap doesn't exist in the official osu! server
      }

      Beatmapset beatmapset = beatmapsetService.findByBeatmapsetId(osuApiBeatmap.getBeatmapsetId());

      if (beatmapset == null) {
        logger.info("Creating new beatmapset: {}", osuApiBeatmap.getBeatmapsetId());

        beatmapset = new Beatmapset();
        beatmapset.setId(osuApiBeatmap.getBeatmapsetId());
        beatmapset.setTitle(osuApiBeatmap.getTitle());
        beatmapset.setArtist(osuApiBeatmap.getArtist());
        beatmapset.setSource(osuApiBeatmap.getSource());
        beatmapset.setCreator(osuApiBeatmap.getCreator());
        beatmapset.setTags(osuApiBeatmap.getTags());
        beatmapset.setSubmissionStatus(BeatmapRankedStatus.fromValue(osuApiBeatmap.getApproved()));
        beatmapset.setHasVideo(osuApiBeatmap.getVideo());
        beatmapset.setHasStoryboard(osuApiBeatmap.getStoryboard());
        beatmapset.setSubmissionDate(osuApiBeatmap.getSubmitDate());
        beatmapset.setApprovedDate(
            osuApiBeatmap.getApprovedDate() != null ? osuApiBeatmap.getApprovedDate() : null);
        beatmapset.setLastUpdated(osuApiBeatmap.getLastUpdate());
        beatmapset.setTotalPlaycount(0); // dont save bancho playcount, we will track our own
        beatmapset.setLanguageId(osuApiBeatmap.getLanguageId());
        beatmapset.setGenreId(osuApiBeatmap.getGenreId());
        beatmapsetService.create(beatmapset);
      }

      List<pe.nanamochi.banchus.entities.osuapi.Beatmap> osuApiBeatmaps =
          osuApi.getBeatmaps(osuApiBeatmap.getBeatmapsetId());

      if (osuApiBeatmaps == null || osuApiBeatmaps.isEmpty()) {
        logger.error(
            "Failed to fetch beatmaps for beatmapset: {}", osuApiBeatmap.getBeatmapsetId());
        return "";
      }

      for (pe.nanamochi.banchus.entities.osuapi.Beatmap b : osuApiBeatmaps) {
        Beatmap existingBeatmap = beatmapService.findByMd5(b.getFileMd5());
        if (existingBeatmap != null) {
          if (b.getFileMd5().equals(beatmapMd5)) {
            beatmap = existingBeatmap;
          }
          continue;
        }

        Beatmap newBeatmap = new Beatmap();
        newBeatmap.setId(b.getBeatmapId());
        newBeatmap.setBeatmapset(beatmapset);
        newBeatmap.setMode(Mode.fromValue(b.getMode()));
        newBeatmap.setMd5(b.getFileMd5());
        newBeatmap.setStatus(BeatmapRankedStatus.fromValue(b.getApproved()));
        newBeatmap.setVersion(b.getVersion());
        newBeatmap.setSubmissionDate(b.getSubmitDate());
        newBeatmap.setLastUpdated(b.getLastUpdate());
        newBeatmap.setPlaycount(0); // dont save bancho playcount, we will track our own
        newBeatmap.setPasscount(0); // dont save bancho passcount, we will track our own
        newBeatmap.setTotalLength(b.getTotalLength());
        newBeatmap.setDrainLength(b.getHitLength());
        newBeatmap.setCountNormal(b.getCountNormal());
        newBeatmap.setCountSlider(b.getCountSlider());
        newBeatmap.setCountSpinner(b.getCountSpinner());
        newBeatmap.setMaxCombo(b.getMaxCombo());
        newBeatmap.setBpm(b.getBpm());
        newBeatmap.setCs(b.getDiffSize());
        newBeatmap.setAr(b.getDiffApproach());
        newBeatmap.setOd(b.getDiffOverall());
        newBeatmap.setHp(b.getDiffDrain());
        newBeatmap.setStarRating(b.getDifficultyRating());

        beatmapService.create(newBeatmap);
        beatmapService.getOrDownloadOsuFile(b.getBeatmapId(), b.getFileMd5());

        if (b.getFileMd5().equals(beatmapMd5)) {
          beatmap = newBeatmap;
        }
      }
    }

    // Check if the beatmap has a update in the official osu! servers
    List<pe.nanamochi.banchus.entities.osuapi.Beatmap> osuApiBeatmaps =
        osuApi.getBeatmaps(beatmap.getBeatmapset().getId());

    if (osuApiBeatmaps != null && !osuApiBeatmaps.isEmpty()) {
      Instant localLastUpdate = beatmap.getBeatmapset().getLastUpdated();
      Instant remoteLastUpdate =
          osuApiBeatmaps.stream()
              .map(pe.nanamochi.banchus.entities.osuapi.Beatmap::getLastUpdate)
              .max(Instant::compareTo)
              .orElse(localLastUpdate);

      if (localLastUpdate.isBefore(remoteLastUpdate)) {
        logger.info(
            "Beatmapset has updates, downloading new files for id: {}",
            beatmap.getBeatmapset().getId());

        // Update and overwrite the local beatmap files
        for (pe.nanamochi.banchus.entities.osuapi.Beatmap b : osuApiBeatmaps) {
          beatmapService.getOrDownloadOsuFile(b.getBeatmapId(), b.getFileMd5());

          Beatmap localBeatmap = beatmapService.findByMd5(b.getFileMd5());
          if (localBeatmap != null) {
            localBeatmap.setLastUpdated(b.getLastUpdate());
            localBeatmap.setStarRating(b.getDifficultyRating());
            localBeatmap.setMd5(b.getFileMd5());
            // TODO: add more fields to update
            beatmapService.update(localBeatmap);
          }
        }

        Beatmapset beatmapset = beatmap.getBeatmapset();
        beatmapset.setLastUpdated(remoteLastUpdate);
        beatmapsetService.update(beatmapset);
      }
    }

    // TODO: create filter parameters for score fetching based on the leaderboard type
    if (LeaderboardType.fromValue(leaderboardType) == LeaderboardType.MODS) {
      // If the leaderboard type is MODS, we need to filter by the mods
    }

    // Fetch our top 50 scores for the leaderboard
    List<Score> scores =
        scoreService.getBeatmapLeaderboard(
            beatmap, mode, Mods.toBitmask(mods), SubmissionStatus.BEST);
    System.out.println(scores);

    // Fetch our personal best score for the beatmap
    Score personalBestScore = scoreService.getBestScore(beatmap, user);

    return formatLeaderboardResponse(scores, personalBestScore, user, beatmap);
  }

  public String formatLeaderboardResponse(
      List<Score> leaderboardScores, Score personalBestScore, User account, Beatmap beatmap) {
    StringBuilder response = new StringBuilder();
    response
        .append(BeatmapWebRankedStatus.convertToWebStatus(beatmap.getStatus()))
        .append("|")
        .append("false")
        .append("|")
        .append(beatmap.getId())
        .append("|")
        .append(beatmap.getBeatmapset().getId())
        .append("|")
        .append(leaderboardScores.size())
        .append("|")
        .append("0")
        .append("|\n");

    String beatmapName =
        beatmap.getBeatmapset().getArtist()
            + " - "
            + beatmap.getBeatmapset().getTitle()
            + " ["
            + beatmap.getVersion()
            + "]";

    response.append("0\n").append(beatmapName).append("\n").append("0.0\n"); // TODO: rating

    if (personalBestScore == null) {
      response.append("\n");
    } else {
      response
          .append(personalBestScore.getId())
          .append("|")
          .append(account.getUsername())
          .append("|")
          .append(personalBestScore.getScore())
          .append("|")
          .append(personalBestScore.getHighestCombo())
          .append("|")
          .append(personalBestScore.getNum50s())
          .append("|")
          .append(personalBestScore.getNum100s())
          .append("|")
          .append(personalBestScore.getNum300s())
          .append("|")
          .append(personalBestScore.getNumMisses())
          .append("|")
          .append(personalBestScore.getNumKatus())
          .append("|")
          .append(personalBestScore.getNumGekis())
          .append("|")
          .append(personalBestScore.isFullCombo() ? "1" : "0")
          .append("|")
          .append(personalBestScore.getMods())
          .append("|")
          .append(account.getId())
          .append("|")
          .append("1")
          .append("|") // TODO: leaderboard rank
          .append(personalBestScore.getCreatedAt().getEpochSecond())
          .append("|")
          .append("1\n"); // TODO: has replay
    }

    if (leaderboardScores.isEmpty()) {
      response.append("\n");
    } else {
      int leaderboardRank = 0;

      for (Score score : leaderboardScores) {
        User scoreUser = score.getUser();
        if (scoreUser == null) {
          continue;
        }
        response
            .append(score.getId())
            .append("|")
            .append(scoreUser.getUsername())
            .append("|")
            .append(score.getScore())
            .append("|")
            .append(score.getHighestCombo())
            .append("|")
            .append(score.getNum50s())
            .append("|")
            .append(score.getNum100s())
            .append("|")
            .append(score.getNum300s())
            .append("|")
            .append(score.getNumMisses())
            .append("|")
            .append(score.getNumKatus())
            .append("|")
            .append(score.getNumGekis())
            .append("|")
            .append(score.isFullCombo() ? "1" : "0")
            .append("|")
            .append(score.getMods())
            .append("|")
            .append(scoreUser.getId())
            .append("|")
            .append(leaderboardRank)
            .append("|")
            .append(score.getCreatedAt().getEpochSecond())
            .append("|")
            .append("1\n"); // TODO: has replay

        leaderboardRank++;
      }

      // remove trailing newline
      response.setLength(response.length() - 1);
    }

    return response.toString();
  }
}
