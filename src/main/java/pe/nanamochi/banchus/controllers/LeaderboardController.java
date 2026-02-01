package pe.nanamochi.banchus.controllers;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<String> getScores(
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
    Mode mode = Mode.fromValue(gamemode);
    List<Mods> mods = Mods.filterInvalidModCombinations(Mods.fromBitmask(modsBitmask), mode);

    User user = userService.login(username, passwordMd5);
    if (user == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Session session = sessionService.getPrimarySessionByUsername(user.getUsername());
    if (session == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // TODO: update user stats if they have changed

    // Fetch the beatmap with this md5
    Beatmap beatmap = beatmapService.findByMd5(beatmapMd5);

    List<pe.nanamochi.banchus.entities.osuapi.Beatmap> osuApiBeatmaps = null;

    if (beatmap == null) {
      var osuApiBeatmap = osuApi.getBeatmap(beatmapMd5);
      if (osuApiBeatmap == null) {
        return ResponseEntity.ok("-1|false");
      }

      Beatmapset beatmapset = beatmapsetService.findByBeatmapsetId(osuApiBeatmap.getBeatmapsetId());

      if (beatmapset == null) {
        beatmapset = beatmapsetService.createFromApi(osuApiBeatmap);
        beatmapsetService.create(beatmapset);
      }

      osuApiBeatmaps = osuApi.getBeatmaps(osuApiBeatmap.getBeatmapsetId());
      if (osuApiBeatmaps == null || osuApiBeatmaps.isEmpty()) {
        return ResponseEntity.ok("-1|false");
      }

      for (var b : osuApiBeatmaps) {
        Beatmap existing = beatmapService.findByMd5(b.getFileMd5());
        if (existing != null) {
          if (b.getFileMd5().equals(beatmapMd5)) {
            beatmap = existing;
          }
          continue;
        }

        Beatmap newBeatmap = beatmapService.createFromApi(b);
        newBeatmap.setBeatmapset(beatmapset);

        beatmapService.create(newBeatmap);
        beatmapService.getOrDownloadOsuFile(b.getBeatmapId(), b.getFileMd5());

        if (b.getFileMd5().equals(beatmapMd5)) {
          beatmap = newBeatmap;
        }
      }
    }

    if (osuApiBeatmaps == null) {
      osuApiBeatmaps = osuApi.getBeatmaps(beatmap.getBeatmapset().getId());
    }

    if (osuApiBeatmaps != null && !osuApiBeatmaps.isEmpty()) {
      Instant localLastUpdate = beatmap.getBeatmapset().getLastUpdated();
      Instant remoteLastUpdate =
          osuApiBeatmaps.stream()
              .map(pe.nanamochi.banchus.entities.osuapi.Beatmap::getLastUpdate)
              .max(Instant::compareTo)
              .orElse(localLastUpdate);

      if (localLastUpdate.isBefore(remoteLastUpdate)) {
        // Update and overwrite the local beatmap files
        logger.info(
            "Beatmapset has updates, downloading new files for id: {}",
            beatmap.getBeatmapset().getId());

        Beatmapset beatmapset = beatmap.getBeatmapset();
        // TODO: add more fields to update
        beatmapset.setLastUpdated(remoteLastUpdate);
        beatmapsetService.update(beatmapset);

        for (var b : osuApiBeatmaps) {
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
      }
    }

    // Create filter parameters for score fetching based on the leaderboard type
    LeaderboardType type = LeaderboardType.fromValue(leaderboardType);
    Integer modsToFilter = null;
    CountryCode country = null;

    switch (type) {
      case MODS -> modsToFilter = Mods.toBitmask(mods);
      case COUNTRY -> country = user.getCountry();
      case FRIENDS -> logger.warn("Friends leaderboard not implemented yet");
    }

    // Fetch our top 50 scores for the leaderboard
    List<Score> scores =
        scoreService.getBeatmapLeaderboard(
            beatmap, mode, modsToFilter, SubmissionStatus.BEST, country);

    // Fetch our personal best score for the beatmap
    Score personalBestScore = scoreService.getBestScore(beatmap, user);

    return ResponseEntity.ok(formatLeaderboardResponse(scores, personalBestScore, user, beatmap));
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
