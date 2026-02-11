package pe.nanamochi.banchus.services.gameplay;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.commons.*;
import pe.nanamochi.banchus.entities.db.*;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.server.MessagePacket;
import pe.nanamochi.banchus.packets.server.UserStatsPacket;
import pe.nanamochi.banchus.repositories.db.ScoreRepository;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.beatmap.BeatmapService;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.gameplay.performance.CalculatorType;
import pe.nanamochi.banchus.services.infra.StorageService;
import pe.nanamochi.banchus.services.player.RankingService;
import pe.nanamochi.banchus.services.player.StatService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;
import pe.nanamochi.banchus.utils.Rijndael;

@Service
@RequiredArgsConstructor
public class ScoreService {
  private final ScoreRepository scoreRepository;
  private final SessionService sessionService;
  private final ChartService chartService;
  private final RankingService rankingService;
  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;
  private final StorageService storageService;
  private final StatService statService;
  private final BeatmapService beatmapService;
  private final PerformanceService performanceService;

  @Value("${banchus.pp-calculator-type}")
  private String ppCalculatorType;

  public Score getScoreById(Integer id) {
    return scoreRepository.findById(id).orElse(null);
  }

  public Score saveScore(Score score) {
    return scoreRepository.save(score);
  }

  public Score updateScore(Score score) {
    if (!scoreRepository.existsById(score.getId())) {
      throw new IllegalArgumentException("Score not found: " + score.getId());
    }
    return scoreRepository.save(score);
  }

  public List<Score> getUserTop100(User user, Mode mode) {
    return scoreRepository
        .findTop100ByUserAndModeAndSubmissionStatusInAndBeatmapStatusInOrderByPerformancePointsDesc(
            user,
            mode,
            List.of(SubmissionStatus.BEST),
            List.of(BeatmapRankedStatus.RANKED, BeatmapRankedStatus.APPROVED));
  }

  public int getUserBestScoresCount(User user, Mode mode) {
    return scoreRepository.countByUserAndModeAndSubmissionStatusInAndBeatmapStatusIn(
        user,
        mode,
        List.of(SubmissionStatus.BEST),
        List.of(BeatmapRankedStatus.RANKED, BeatmapRankedStatus.APPROVED));
  }

  public Score getBestScore(Beatmap beatmap, User user) {
    return scoreRepository.findFirstByBeatmapAndUserAndSubmissionStatusOrderByPerformancePointsDesc(
        beatmap, user, SubmissionStatus.BEST);
  }

  public List<Score> getBeatmapLeaderboard(
      Beatmap beatmap, Mode mode, Integer mods, SubmissionStatus status, CountryCode country) {
    if (mods != null) {
      if (country != null) {
        return scoreRepository.findTop50UnrestrictedWithModsByCountry(
            beatmap, mode, mods, status, country);
      }

      return scoreRepository.findTop50UnrestrictedWithMods(beatmap, mode, mods, status);
    }

    if (country != null) {
      return scoreRepository.findTop50UnrestrictedByCountry(beatmap, mode, status, country);
    }

    return scoreRepository.findTop50Unrestricted(beatmap, mode, status);
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

  public ParsedScoreDTO parseScoreData(
      HttpServletRequest request,
      String ivB64,
      String osuVersion,
      Integer scoreTime,
      Integer failTime)
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

    User user = new User();
    user.setUsername(scoreData[1].stripTrailing());

    Beatmap beatmap = new Beatmap();
    beatmap.setMd5(scoreData[0]);

    Score score = new Score();
    score.setUser(user); // Temporary user, used to login later
    score.setOnlineChecksum(scoreData[2]);
    score.setBeatmap(beatmap); // Temporary beatmap, used to check later
    score.setScore(Integer.parseInt(scoreData[9]));
    score.setHighestCombo(Integer.parseInt(scoreData[10]));
    score.setFullCombo(Boolean.parseBoolean(scoreData[11]));
    score.setMods(Integer.parseInt(scoreData[13]));
    score.setNum300s(Integer.parseInt(scoreData[3]));
    score.setNum100s(Integer.parseInt(scoreData[4]));
    score.setNum50s(Integer.parseInt(scoreData[5]));
    score.setNumMisses(Integer.parseInt(scoreData[8]));
    score.setNumGekis(Integer.parseInt(scoreData[6]));
    score.setNumKatus(Integer.parseInt(scoreData[7]));
    score.setGrade(scoreData[12]);
    score.setMode(Mode.fromValue(Integer.parseInt(scoreData[15])));
    score.setPassed(Boolean.parseBoolean(scoreData[14]));
    score.setTimeElapsed(Boolean.parseBoolean(scoreData[14]) ? scoreTime : failTime);

    return new ParsedScoreDTO(score, replayBytes);
  }

  public String processScoreSubmission(
      ParsedScoreDTO parsedScore, User user, Beatmap beatmap, Session session) throws Exception {
    Score score = parsedScore.getScore();
    score.setMods(Mods.filterInvalidModCombinations(score.getMods(), score.getMode()));
    score.setBeatmap(beatmap);
    score.updateAccuracy();

    beatmapService.getOrDownloadOsuFile(beatmap.getId(), beatmap.getMd5());
    double pp =
        performanceService.calculate(
            beatmapService.getBeatmapPath(beatmap.getId()).toAbsolutePath().toString(),
            score,
            CalculatorType.fromAlias(ppCalculatorType));

    SubmissionStatus submissionStatus;
    Score previousBestScore = null;

    if (score.isPassed()) {
      previousBestScore = getBestScore(beatmap, user);
      boolean isNewBest =
          previousBestScore == null || pp > previousBestScore.getPerformancePoints();

      if (isNewBest) {
        submissionStatus = SubmissionStatus.BEST;
        if (previousBestScore != null) {
          previousBestScore.setSubmissionStatus(SubmissionStatus.SUBMITTED);
          updateScore(previousBestScore);
        }
      } else {
        submissionStatus = SubmissionStatus.SUBMITTED;
      }
    } else {
      submissionStatus = SubmissionStatus.FAILED;
    }

    score.setUser(user);
    score.setSubmissionStatus(submissionStatus);
    score.setPerformancePoints(pp);

    // Persist new score to database
    score = saveScore(score);

    // Save replay in our filesystem
    storageService.saveReplay(score.getId(), parsedScore.getReplayBytes());

    // Update beatmap stats (plays, passes)
    beatmap.setPlaycount(beatmap.getPlaycount() + 1);
    beatmap.setPasscount(
        score.getSubmissionStatus() != SubmissionStatus.FAILED
            ? beatmap.getPasscount() + 1
            : beatmap.getPasscount());
    beatmapService.update(beatmap);

    Stat modeStats = statService.getStats(user, score.getMode());
    List<Score> top100Scores = getUserTop100(user, score.getMode());
    int totalScoreCount = getUserBestScoresCount(user, score.getMode());

    // Calculate new overall accuracy
    double weightedAccuracy = statService.calculateWeightedAccuracy(top100Scores);
    double bonusAccuracy = 0.0;
    if (totalScoreCount > 0) {
      bonusAccuracy = 100.0 / (20 * (1 - Math.pow(0.95, totalScoreCount)));
    }
    double totalAccuracy = (weightedAccuracy * bonusAccuracy) / 100.0;

    // Calculate new overall pp
    double weightedPp = statService.calculateWeightedPp(top100Scores);
    double bonusPp = (416.6667 * (1 - Math.pow(0.9994, totalScoreCount)));
    double totalPp = Math.round(weightedPp + bonusPp);

    // Create a copy of the previous gamemode's stats.
    // We will use this to construct overall ranking charts for the client
    Stat previousModeStats = (Stat) modeStats.clone();
    int previousGlobalRank = Math.toIntExact(rankingService.getGlobalRank(score.getMode(), user));
    long newRankedScore = modeStats.getRankedScore();

    if (score.getSubmissionStatus() == SubmissionStatus.BEST
        && (beatmap.getStatus() == BeatmapRankedStatus.RANKED
            || beatmap.getStatus() == BeatmapRankedStatus.APPROVED)) {
      newRankedScore += score.getScore();

      if (previousBestScore != null) {
        newRankedScore -= previousBestScore.getScore();
      }
    }

    // Update this gamemode's stats with our new score submission
    modeStats.setGamemode(score.getMode());
    modeStats.setTotalScore(modeStats.getTotalScore() + score.getScore());
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

    rankingService.updateRanking(score.getMode(), user, modeStats);

    // Send account stats to all other osu! sessions if we're not restricted
    List<Session> osuSessionsToNotify;
    if (user.isRestricted()) {
      osuSessionsToNotify = List.of(session);
    } else {
      osuSessionsToNotify = sessionService.getAllSessions();
    }

    int ownGlobalRank = Math.toIntExact(rankingService.getGlobalRank(score.getMode(), user));

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
              score.getMode(),
              session.getBeatmapId(),
              modeStats.getRankedScore(),
              (float) modeStats.getAccuracy(),
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
            channelMembersService.getMembers(announceChannel.getId());
        for (UUID osuSessionId : announceChannelMembers) {
          packetBundleService.enqueue(osuSessionId, new PacketBundle(stream.toByteArray()));
        }
      }
    }

    // TODO: unlock achievements

    // Build beatmap ranking chart values
    return chartService.buildCharts(
        beatmap,
        score,
        previousBestScore,
        user,
        previousModeStats,
        modeStats,
        previousGlobalRank,
        ownGlobalRank);
  }

  @AllArgsConstructor
  @Getter
  public static class ParsedScoreDTO {
    public Score score;
    public byte[] replayBytes;
  }
}
