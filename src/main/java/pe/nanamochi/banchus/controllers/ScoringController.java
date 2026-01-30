package pe.nanamochi.banchus.controllers;

import io.github.nanamochi.rosu_pp_jar.Mods;
import io.github.nanamochi.rosu_pp_jar.Performance;
import io.github.nanamochi.rosu_pp_jar.PerformanceAttributes;
import io.github.nanamochi.rosu_pp_jar.RosuException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.nanamochi.banchus.entities.*;
import pe.nanamochi.banchus.entities.db.*;
import pe.nanamochi.banchus.entities.osuapi.Beatmap;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.server.MessagePacket;
import pe.nanamochi.banchus.packets.server.UserStatsPacket;
import pe.nanamochi.banchus.services.*;
import pe.nanamochi.banchus.utils.OsuApi;
import pe.nanamochi.banchus.utils.Rijndael;

@RestController
@RequestMapping("/web")
public class ScoringController {
  private static final Logger logger = LoggerFactory.getLogger(ScoringController.class);

  @Autowired private UserService userService;
  @Autowired private SessionService sessionService;
  @Autowired private ScoreService scoreService;
  @Autowired private BeatmapsetService beatmapsetService;
  @Autowired private BeatmapService beatmapService;
  @Autowired private ReplayService replayService;
  @Autowired private StatService statService;
  @Autowired private OsuApi osuApi;
  @Autowired private PacketWriter packetWriter;
  @Autowired private PacketBundleService packetBundleService;
  @Autowired private ChannelService channelService;
  @Autowired private ChannelMembersRedisService channelMembersRedisService;
  @Autowired private RankingService rankingService;

  @PostMapping(
      value = "/osu-submit-modular-selector.php",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String scoreSubmission(
      HttpServletRequest request,
      @RequestParam(value = "ft", required = false) Integer failTime,
      @RequestParam(value = "iv", required = false) String ivB64,
      @RequestParam(value = "st", required = false) Integer scoreTime,
      @RequestParam(value = "pass", required = false) String passwordMd5,
      @RequestParam(value = "osuver", required = false) String osuVersion,
      @RequestParam(value = "s", required = false) String clientHashB64,
      @RequestPart(value = "i", required = false) MultipartFile flCheatScreenshot)
      throws Exception {
    byte[] iv = Base64.getDecoder().decode(ivB64);

    // The bancho protocol uses the "score" parameter name for both the base64'ed score data,
    // and the replay file in the multipart. @RequestPart can´t handle it well, so we manually
    // handle it here with HttpServletRequest
    List<Part> scoreParts =
        request.getParts().stream().filter(p -> p.getName().equals("score")).toList();
    Part scoreDataPart = scoreParts.get(0);
    Part replayPart = scoreParts.get(1);

    String scoreDataAesB64 =
        new String(scoreDataPart.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    byte[] encryptedScoreData = Base64.getDecoder().decode(scoreDataAesB64);
    byte[] replayBytes = replayPart.getInputStream().readAllBytes();

    // Ensure AES key is exactly 32 bytes
    String keyStr = ("osu!-scoreburgr---------" + osuVersion);
    keyStr = String.format("%-32s", keyStr).substring(0, 32);
    byte[] aesKey = keyStr.getBytes(StandardCharsets.UTF_8);

    // Decrypt score data
    byte[] decryptedBytes = Rijndael.decrypt(encryptedScoreData, aesKey, iv);
    String decrypted = new String(decryptedBytes, StandardCharsets.UTF_8);
    String[] scoreData = decrypted.split(":");

    if (scoreData.length < 13) {
      logger.info("A submitted score has a malformed score data.");
      return "error: " + ScoreSubmissionErrors.NO.getValue();
    }

    String username = scoreData[1].stripTrailing();

    User user = userService.login(username, passwordMd5);
    if (user == null) {
      return "error: " + ScoreSubmissionErrors.NEEDS_AUTHENTICATION.getValue();
    }

    Session session = sessionService.getPrimarySessionByUsername(username);
    if (session == null) {
      return "error: " + ScoreSubmissionErrors.NEEDS_AUTHENTICATION.getValue();
    }

    // TODO: handle differently depending on beatmap ranked status

    boolean isPassed = Boolean.parseBoolean(scoreData[14]);
    String beatmapMd5 = scoreData[0];
    String onlineChecksum = scoreData[2];
    int num300s = Integer.parseInt(scoreData[3]);
    int num100s = Integer.parseInt(scoreData[4]);
    int num50s = Integer.parseInt(scoreData[5]);
    int numGekis = Integer.parseInt(scoreData[6]);
    int numKatus = Integer.parseInt(scoreData[7]);
    int numMisses = Integer.parseInt(scoreData[8]);
    int scorePoints = Integer.parseInt(scoreData[9]);
    int highestCombo = Integer.parseInt(scoreData[10]);
    boolean fullCombo = Boolean.parseBoolean(scoreData[11]);
    String grade = scoreData[12];
    int mods = Integer.parseInt(scoreData[13]);
    int mode = Integer.parseInt(scoreData[15]);

    // Do a request to the osu!api to get beatmap info, because we need to get the beatmap_id from
    // the md5 hash, by default all beatmaps are returned independtly from the hash, so we need to
    // filter them here
    Beatmap osuApibeatmap = osuApi.getBeatmap(beatmapMd5);

    if (osuApibeatmap == null) {
      return "error: "
          + ScoreSubmissionErrors.BEATMAP_UNRANKED.getValue(); // TODO: is this the correct error?
    }

    // Check if the beatmapset exists in our database, if not, store the beatmapset and the beatmap
    Beatmapset beatmapset = beatmapsetService.findByBeatmapsetId(osuApibeatmap.getBeatmapsetId());
    if (beatmapset == null) {
      beatmapset = new Beatmapset();
      beatmapset.setId(osuApibeatmap.getBeatmapsetId());
      beatmapset.setTitle(osuApibeatmap.getTitle());
      beatmapset.setArtist(osuApibeatmap.getArtist());
      beatmapset.setSource(osuApibeatmap.getSource());
      beatmapset.setCreator(osuApibeatmap.getCreator());
      beatmapset.setTags(osuApibeatmap.getTags());
      beatmapset.setSubmissionStatus(BeatmapRankedStatus.fromValue(osuApibeatmap.getApproved()));
      beatmapset.setHasVideo(osuApibeatmap.getVideo());
      beatmapset.setHasStoryboard(osuApibeatmap.getStoryboard());
      beatmapset.setSubmissionDate(osuApibeatmap.getSubmitDate());
      beatmapset.setApprovedDate(
          osuApibeatmap.getApprovedDate() != null ? osuApibeatmap.getApprovedDate() : null);
      beatmapset.setLastUpdated(osuApibeatmap.getLastUpdate());
      beatmapset.setTotalPlaycount(0); // dont save bancho playcount, we will track our own
      beatmapset.setLanguageId(osuApibeatmap.getLanguageId());
      beatmapset.setGenreId(osuApibeatmap.getGenreId());
      beatmapsetService.create(beatmapset);

      List<Beatmap> osuApibeatmaps = osuApi.getBeatmaps(osuApibeatmap.getBeatmapsetId());
      for (Beatmap b : osuApibeatmaps) {
        pe.nanamochi.banchus.entities.db.Beatmap beatmap =
            new pe.nanamochi.banchus.entities.db.Beatmap();
        beatmap.setId(b.getBeatmapId());
        beatmap.setBeatmapset(beatmapset);
        beatmap.setMode(Mode.fromValue(b.getMode()));
        beatmap.setMd5(b.getFileMd5());
        beatmap.setStatus(BeatmapRankedStatus.fromValue(b.getApproved()));
        beatmap.setVersion(b.getVersion());
        beatmap.setSubmissionDate(b.getSubmitDate());
        beatmap.setLastUpdated(b.getLastUpdate());
        beatmap.setPlaycount(0); // dont save bancho playcount, we will track our own
        beatmap.setPasscount(0); // dont save bancho passcount, we will track our own
        beatmap.setTotalLength(b.getTotalLength());
        beatmap.setDrainLength(b.getHitLength());
        beatmap.setCountNormal(b.getCountNormal());
        beatmap.setCountSlider(b.getCountSlider());
        beatmap.setCountSpinner(b.getCountSpinner());
        beatmap.setMaxCombo(b.getMaxCombo());
        beatmap.setBpm(b.getBpm());
        beatmap.setCs(b.getDiffSize());
        beatmap.setAr(b.getDiffApproach());
        beatmap.setOd(b.getDiffOverall());
        beatmap.setHp(b.getDiffDrain());
        beatmap.setStarRating(b.getDifficultyRating());

        beatmapService.create(beatmap);
        beatmapService.getOrDownloadOsuFile(b.getBeatmapId(), b.getFileMd5());
      }
    }

    Score score = new Score();
    score.setUser(user);
    score.setOnlineChecksum(onlineChecksum);
    score.setBeatmap(beatmapService.findByMd5(beatmapMd5));
    score.setScore(scorePoints);
    score.setHighestCombo(highestCombo);
    score.setFullCombo(fullCombo);
    score.setMods(mods);
    score.setNum300s(num300s);
    score.setNum100s(num100s);
    score.setNum50s(num50s);
    score.setNumMisses(numMisses);
    score.setNumGekis(numGekis);
    score.setNumKatus(numKatus);
    score.setGrade(grade);
    score.setMode(Mode.fromValue(mode));
    score.setTimeElapsed(isPassed ? scoreTime : failTime);

    double pp =
        calculatePp(
            beatmapService.getOrDownloadOsuFile(osuApibeatmap.getBeatmapId(), beatmapMd5), score);
    float accuracy = calculateAccuracy(score);

    pe.nanamochi.banchus.entities.db.Beatmap beatmap = beatmapService.findByMd5(beatmapMd5);
    SubmissionStatus submissionStatus;
    Score previousBestScore = null;

    if (isPassed) {
      previousBestScore = scoreService.getBestScore(beatmap, user);
      boolean isNewBest =
          previousBestScore == null || pp > previousBestScore.getPerformancePoints();

      if (isNewBest) {
        submissionStatus = SubmissionStatus.BEST;
        if (previousBestScore != null) {
          previousBestScore.setSubmissionStatus(SubmissionStatus.SUBMITTED);
          scoreService.updateScore(previousBestScore);
        }
      } else {
        submissionStatus = SubmissionStatus.SUBMITTED;
      }
    } else {
      submissionStatus = SubmissionStatus.FAILED;
    }

    score.setSubmissionStatus(submissionStatus);
    score.setPerformancePoints(pp);
    score.setAccuracy(accuracy);

    // Persist new score to database
    score = scoreService.saveScore(score);

    // Save replay in our filesystem
    replayService.saveReplay(score.getId(), replayBytes);

    // Update beatmap stats (plays, passes)
    beatmap.setPlaycount(beatmap.getPlaycount() + 1);
    beatmap.setPasscount(
        score.getSubmissionStatus() != SubmissionStatus.FAILED
            ? beatmap.getPasscount() + 1
            : beatmap.getPasscount());
    beatmapService.update(beatmap);

    Stat modeStats = statService.getStats(user, score.getMode());
    System.out.println("Mode stats finded: " + modeStats);
    List<Score> top100Scores = scoreService.getUserTop100(user, score.getMode());
    int totalScoreCount = scoreService.getUserBestScoresCount(user, score.getMode());

    // Calculate new overall accuracy
    float weightedAccuracy = calculateWeightedAccuracy(top100Scores);
    float bonusAccuracy = 0.0f;
    if (totalScoreCount > 0) {
      bonusAccuracy = (float) (100.0f / (20 * (1 - Math.pow(0.95f, totalScoreCount))));
    }
    float totalAccuracy = (weightedAccuracy * bonusAccuracy) / 100.0f;

    // Calculate new overall pp
    float weightedPp = calculateWeightedPp(top100Scores);
    float bonusPp = (float) (416.6667f * (1 - Math.pow(0.9994f, totalScoreCount)));
    float totalPp = Math.round(weightedPp + bonusPp);

    // Create a copy of the previous gamemode's stats.
    // We will use this to construct overall ranking charts for the client
    Stat previousModeStats = (Stat) modeStats.clone();
    int previousGlobalRank =
        Math.toIntExact(rankingService.getGlobalRank(Mode.fromValue(mode), user));
    long newRankedScore = modeStats.getRankedScore();

    if (score.getSubmissionStatus() == SubmissionStatus.BEST
        && (beatmap.getStatus() == BeatmapRankedStatus.RANKED
            || beatmap.getStatus() == BeatmapRankedStatus.APPROVED)) {
      newRankedScore += scorePoints;

      if (previousBestScore != null) {
        newRankedScore -= previousBestScore.getScore();
      }
    }

    // Update this gamemode's stats with our new score submission
    modeStats.setGamemode(score.getMode());
    modeStats.setTotalScore(modeStats.getTotalScore() + scorePoints);
    modeStats.setRankedScore(newRankedScore);
    modeStats.setPerformancePoints((int) totalPp);
    modeStats.setPlayCount(modeStats.getPlayCount() + 1);
    modeStats.setPlayTime(modeStats.getPlayTime() + score.getTimeElapsed());
    modeStats.setAccuracy(totalAccuracy);
    modeStats.setHighestCombo(Math.max(modeStats.getHighestCombo(), score.getHighestCombo()));
    modeStats.setTotalHits(
        modeStats.getTotalHits()
            + score.getNum300s()
            + score.getNum100s()
            + score.getNum50s()
            + score.getNumMisses());
    modeStats.setXhCount(modeStats.getXhCount() + (score.getGrade().equals("XH") ? 1 : 0));
    modeStats.setXCount(modeStats.getXCount() + (score.getGrade().equals("X") ? 1 : 0));
    modeStats.setShCount(modeStats.getShCount() + (score.getGrade().equals("SH") ? 1 : 0));
    modeStats.setSCount(modeStats.getSCount() + (score.getGrade().equals("S") ? 1 : 0));
    modeStats.setACount(modeStats.getACount() + (score.getGrade().equals("A") ? 1 : 0));
    statService.update(modeStats);

    rankingService.updateRanking(Mode.fromValue(mode), user, modeStats);

    // Send account stats to all other osu! sessions if we're not restricted
    List<Session> osuSessionsToNotify;
    if (user.isRestricted()) {
      osuSessionsToNotify = List.of(session);
    } else {
      osuSessionsToNotify = sessionService.getAllSessions();
    }

    int ownGlobalRank = Math.toIntExact(rankingService.getGlobalRank(Mode.fromValue(mode), user));

    for (Session otherOsuSession : osuSessionsToNotify) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(
          stream,
          new UserStatsPacket(
              modeStats.getUser().getId(),
              session.getAction(),
              session.getInfoText(),
              session.getBeatmapMd5(),
              session.getMods(),
              Mode.fromValue(mode),
              session.getBeatmapId(),
              modeStats.getRankedScore(),
              modeStats.getAccuracy(),
              modeStats.getPlayCount(),
              modeStats.getTotalScore(),
              ownGlobalRank,
              modeStats.getPerformancePoints()));
      packetBundleService.enqueue(otherOsuSession.getId(), new PacketBundle(stream.toByteArray()));
    }

    // TODO: calculate score rank on the beatmap
    int scoreRank = 1;

    // If this score is #1, send it to the #announce channel
    if (score.getSubmissionStatus() == SubmissionStatus.BEST && scoreRank == 1) {
      Channel announceChannel = channelService.findByName("#announce");
      if (announceChannel != null) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        packetWriter.writePacket(
            stream,
            new MessagePacket("BanchoBot", beatmap.createBeatmapChatEmbed(), "#announce", 0));
        Set<UUID> announceChannelMembers =
            channelMembersRedisService.getMembers(announceChannel.getId());
        for (UUID osuSessionId : announceChannelMembers) {
          packetBundleService.enqueue(osuSessionId, new PacketBundle(stream.toByteArray()));
        }
      }
    }

    // TODO: unlock achievements

    // Build beatmap ranking chart values
    String beatmapRankBefore = "";
    String beatmapRankedScoreBefore = "";
    String beatmapTotalScoreBefore = "";
    String beatmapMaxComboBefore = "";
    String beatmapAccuracyBefore = "";
    String beatmapPerformancePointsBefore = "";
    if (previousBestScore != null) {
      beatmapRankBefore = String.valueOf(0); // TODO: get previous rank
      beatmapRankedScoreBefore = String.valueOf(previousBestScore.getScore());
      beatmapTotalScoreBefore = String.valueOf(previousBestScore.getScore());
      beatmapMaxComboBefore = String.valueOf(previousBestScore.getHighestCombo());
      beatmapAccuracyBefore = String.format("%.2f", previousBestScore.getAccuracy());
      beatmapPerformancePointsBefore =
          String.valueOf(Math.round(previousBestScore.getPerformancePoints()));
    }

    String beatmapRankAfter = "1";
    String beatmapRankedScoreAfter = String.valueOf(score.getScore());
    String beatmapTotalScoreAfter = String.valueOf(score.getScore());
    String beatmapMaxComboAfter = String.valueOf(score.getHighestCombo());
    String beatmapAccuracyAfter = String.format("%.2f", score.getAccuracy());
    String beatmapPerformancePointsAfter = String.valueOf(Math.round(score.getPerformancePoints()));

    // Build overall ranking chart values
    String overallRankBefore = String.valueOf(previousGlobalRank);
    String overallRankAfter = String.valueOf(ownGlobalRank);
    String overallRankedScoreBefore = String.valueOf(previousModeStats.getRankedScore());
    String overallRankedScoreAfter = String.valueOf(modeStats.getRankedScore());
    String overallTotalScoreBefore = String.valueOf(previousModeStats.getTotalScore());
    String overallTotalScoreAfter = String.valueOf(modeStats.getTotalScore());
    String overallMaxComboBefore = String.valueOf(previousModeStats.getHighestCombo());
    String overallMaxComboAfter = String.valueOf(modeStats.getHighestCombo());
    String overallAccuracyBefore = String.format("%.2f", previousModeStats.getAccuracy());
    String overallAccuracyAfter = String.format("%.2f", modeStats.getAccuracy());
    String overallPerformancePointsBefore =
        String.valueOf(previousModeStats.getPerformancePoints());
    String overallPerformancePointsAfter = String.valueOf(modeStats.getPerformancePoints());

    // Construct response data
    StringBuilder response = new StringBuilder();
    response.append("beatmapId:").append(beatmap.getId()).append("|");
    response.append("beatmapSetId:").append(beatmap.getBeatmapset().getId()).append("|");
    response.append("beatmapPlaycount:").append(beatmap.getPlaycount()).append("|");
    response.append("beatmapPasscount:").append(beatmap.getPasscount()).append("|");
    response.append("approvedDate:").append(beatmap.getSubmissionDate().toString()).append("|");
    response.append("\n|chartId:beatmap|");
    response
        .append("chartUrl:https://osu.ppy.sh/beatmapsets/")
        .append(beatmap.getBeatmapset().getId())
        .append("|");
    response.append("chartName:Beatmap Ranking|");
    response.append("rankBefore:").append(beatmapRankBefore).append("|");
    response.append("rankAfter:").append(beatmapRankAfter).append("|");
    response.append("rankedScoreBefore:").append(beatmapRankedScoreBefore).append("|");
    response.append("rankedScoreAfter:").append(beatmapRankedScoreAfter).append("|");
    response.append("totalScoreBefore:").append(beatmapTotalScoreBefore).append("|");
    response.append("totalScoreAfter:").append(beatmapTotalScoreAfter).append("|");
    response.append("maxComboBefore:").append(beatmapMaxComboBefore).append("|");
    response.append("maxComboAfter:").append(beatmapMaxComboAfter).append("|");
    response.append("accuracyBefore:").append(beatmapAccuracyBefore).append("|");
    response.append("accuracyAfter:").append(beatmapAccuracyAfter).append("|");
    response.append("ppBefore:").append(beatmapPerformancePointsBefore).append("|");
    response.append("ppAfter:").append(beatmapPerformancePointsAfter).append("|");
    response.append("onlineScoreId:").append(score.getId()).append("|");
    response.append("\n|chartId:overall|");
    response.append("chartUrl:https://osu.ppy.sh/u/").append(user.getId()).append("|");
    response.append("chartName:Overall Ranking|");
    response.append("rankBefore:").append(overallRankBefore).append("|");
    response.append("rankAfter:").append(overallRankAfter).append("|");
    response.append("rankedScoreBefore:").append(overallRankedScoreBefore).append("|");
    response.append("rankedScoreAfter:").append(overallRankedScoreAfter).append("|");
    response.append("totalScoreBefore:").append(overallTotalScoreBefore).append("|");
    response.append("totalScoreAfter:").append(overallTotalScoreAfter).append("|");
    response.append("maxComboBefore:").append(overallMaxComboBefore).append("|");
    response.append("maxComboAfter:").append(overallMaxComboAfter).append("|");
    response.append("accuracyBefore:").append(overallAccuracyBefore).append("|");
    response.append("accuracyAfter:").append(overallAccuracyAfter).append("|");
    response.append("ppBefore:").append(overallPerformancePointsBefore).append("|");
    response.append("ppAfter:").append(overallPerformancePointsAfter).append("|");

    // TODO: add newly unlocked achievements to response data

    logger.info(
        "[{}] {} submitted a score | ({}), {}pp",
        score.getMode().getAlias(),
        score.getUser().getUsername(),
        score.getSubmissionStatus(),
        String.format("%.2f", score.getPerformancePoints()));

    return response.toString();
  }

  private float calculateAccuracy(Score score) {
    if (score.getMode() == Mode.OSU) {
      int totalNotes =
          score.getNum300s() + score.getNum100s() + score.getNum50s() + score.getNumMisses();
      return (100.0f
          * ((score.getNum300s() * 300.0f)
              + (score.getNum100s() * 100.0f)
              + (score.getNum50s() * 50.0f))
          / (totalNotes * 300.0f));
    } else if (score.getMode() == Mode.TAIKO) {
      int totalNotes = score.getNum300s() + score.getNum100s() + score.getNumMisses();
      return (100.0f * ((score.getNum100s() * 0.5f) + score.getNum300s()) / totalNotes);
    } else if (score.getMode() == Mode.CATCH) {
      int totalNotes =
          score.getNum300s()
              + score.getNum100s()
              + score.getNum50s()
              + score.getNumKatus()
              + score.getNumMisses();
      return (100.0f * (score.getNum300s() + score.getNum100s() + score.getNum50s())) / totalNotes;
    } else if (score.getMode() == Mode.MANIA) {
      int totalNotes =
          score.getNum300s()
              + score.getNum100s()
              + score.getNum50s()
              + score.getNumGekis()
              + score.getNumKatus()
              + score.getNumMisses();
      return (100.0f
          * ((score.getNum50s() * 50.0f)
              + (score.getNum100s() * 100.0f)
              + (score.getNumKatus() * 200.0f)
              + ((score.getNum300s() + score.getNumGekis()) * 300.0f))
          / (totalNotes * 300.0f));
    } else {
      return 0.0f;
    }
  }

  private double calculatePp(byte[] osuFile, Score score) throws RosuException {
    io.github.nanamochi.rosu_pp_jar.Beatmap rosuBeatmap =
        io.github.nanamochi.rosu_pp_jar.Beatmap.fromBytes(osuFile);
    // rosuBeatmap.convert(GameMode.fromValues) // TODO: implement fromValue in rosu_pp_jar
    Performance performance = Performance.create(rosuBeatmap);
    performance.setMods(Mods.fromBits(score.getMods()));
    performance.setAccuracy((double) score.getAccuracy());
    performance.setNGeki(score.getNumGekis());
    performance.setNGeki(score.getNumKatus());
    performance.setN300(score.getNum300s());
    performance.setN100(score.getNum100s());
    performance.setN50(score.getNum50s());
    performance.setMisses(score.getNumMisses());
    performance.setCombo(score.getHighestCombo());
    PerformanceAttributes attributes = performance.calculate();

    return attributes.pp();
  }

  private float calculateWeightedAccuracy(List<Score> top100Scores) {
    float weightedAccuracy = 0.0f;
    for (int i = 0; i < top100Scores.size(); i++) {
      weightedAccuracy += (float) (top100Scores.get(i).getAccuracy() * Math.pow(0.95f, i));
    }
    return weightedAccuracy;
  }

  private float calculateWeightedPp(List<Score> top100Scores) {
    float weightedPp = 0.0f;
    for (int i = 0; i < top100Scores.size(); i++) {
      weightedPp += (float) (top100Scores.get(i).getPerformancePoints() * Math.pow(0.95f, i));
    }
    return weightedPp;
  }
}
