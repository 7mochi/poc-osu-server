package pe.nanamochi.banchus.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.adapters.OsuApiV2Adapter;
import pe.nanamochi.banchus.config.ScoringConfig;
import pe.nanamochi.banchus.entities.BeatmapStatus;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.ScoreStatus;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.BeatmapRepository;
import pe.nanamochi.banchus.repositories.ScoreRepository;
import pe.nanamochi.banchus.repositories.UserRepository;
import pe.nanamochi.banchus.utils.ScoreDecryption.ParsedScore;

/**
 * Service for handling score submissions and beatmap lookups.
 * - Fetches beatmap info from osu! API v2 if not found locally
 * - Saves beatmaps with server="osuapi" status
 * - Never allows null map_md5
 * - Only saves scores for valid beatmaps
 */
@Service
public class ScoreService {

  private static final Logger logger = LoggerFactory.getLogger(ScoreService.class);

  // Inner class to hold save result with both response and score ID
  public static class SaveScoreResult {
    public Long scoreId;
    public String response;

    public SaveScoreResult(Long scoreId, String response) {
      this.scoreId = scoreId;
      this.response = response;
    }
  }

  @Autowired private ScoreRepository scoreRepository;

  @Autowired private BeatmapRepository beatmapRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private OsuApiV2Adapter osuApiAdapter;

  @Autowired private StatService statService;

  @Autowired private PerformancePointsService ppService;

  @Autowired private ScoringConfig scoringConfig;

  /**
   * Save a score submission.
   * Ensures beatmap exists (fetches from API if needed) before saving score.
   *
   * @param parsedScore The parsed score data from client
   * @param username The username of the player
   */
  public void saveScore(ParsedScore parsedScore, String username) {
    logger.info("╔══════════════════════════════════════════════════════════╗");
    logger.info("║         SCORE SUBMISSION PROCESSING                      ║");
    logger.info("╚══════════════════════════════════════════════════════════╝");
    logger.info("  Username: {}\n" +
        "  Beatmap MD5: {}\n" +
        "  Score: {}\n" +
        "  PP (temp): {}\n" +
        "  Max Combo: {}\n" +
        "  Mods: {}\n" +
        "  Accuracy: {}%\n" +
        "  300s: {}, 100s: {}, 50s: {}, Misses: {}\n" +
        "  Passed: {}",
        username, parsedScore.mapMd5, parsedScore.score, parsedScore.pp,
        parsedScore.maxCombo, parsedScore.mods, String.format("%.2f", parsedScore.accuracy),
        parsedScore.n300, parsedScore.n100, parsedScore.n50, parsedScore.nmiss, parsedScore.passed);

    double accuracy = parsedScore.accuracy;

    // Get or create beatmap
    Beatmap beatmap = getOrFetchBeatmap(parsedScore.mapMd5);
    if (beatmap == null) {
      logger.error("❌ Could not find or fetch beatmap for MD5: {}", parsedScore.mapMd5);
      return;
    }

    logger.info("  ✓ Beatmap resolved: {} - {} [{}]", 
        beatmap.getArtist(), beatmap.getTitle(), beatmap.getVersion());

    // Find user
    User user = userRepository.findByUsername(username);
    if (user == null) {
      logger.error("❌ User not found: {}", username);
      return;
    }

    logger.info("  ✓ User found: {} (ID: {})", user.getUsername(), user.getId());

    // Calculate PP for the score
    double calculatedPP = calculateScorePP(parsedScore, beatmap);
    logger.info("  📊 Calculated PP: {}", String.format("%.2f", calculatedPP));

    // Determine submission status based on whether score was passed
    int status;
    if (!parsedScore.passed) {
      status = ScoreStatus.FAILED.getValue();
      logger.info("ℹ Score not passed, marking as FAILED");
    } else {
      status = ScoreStatus.BEST.getValue();
      logger.info("ℹ Score passed, marking as BEST");
    }

    // Create and save score
    Score score = Score.builder()
        .mapMd5(parsedScore.mapMd5)
        .score(parsedScore.score)
        .pp((float) calculatedPP) // Use calculated PP instead of placeholder
        .acc((float) parsedScore.accuracy)
        .maxCombo(parsedScore.maxCombo)
        .mods(parsedScore.mods)
        .n300(parsedScore.n300)
        .n100(parsedScore.n100)
        .n50(parsedScore.n50)
        .nmiss(parsedScore.nmiss)
        .ngeki(parsedScore.ngeki)
        .nkatu(parsedScore.nkatu)
        .perfect(parsedScore.perfect)
        .status(status)
        .passed(parsedScore.passed)
        .playTime(LocalDateTime.now())
        .gameMode(parsedScore.gamemode)
        .user(user)
        .build();

    Score savedScore = scoreRepository.save(score);
    logger.info("╔══════════════════════════════════════════════════════════╗");
    logger.info("║              ✓✓✓ SCORE SAVED SUCCESSFULLY ✓✓✓           ║");
    logger.info("║  Score ID: {}                                   ║", savedScore.getId());
    logger.info("║  User: {} | Beatmap: {}  ║", username, parsedScore.mapMd5);
    logger.info("║  Score: {} | Accuracy: {}% | Combo: {}    ║", 
        parsedScore.score, String.format("%.2f", accuracy), parsedScore.maxCombo);
    logger.info("║  Status: {} | Passed: {}                             ║", status, parsedScore.passed);
    logger.info("╚══════════════════════════════════════════════════════════╝");

    // Update beatmap play/pass counts
    updateBeatmapPlayPassCounts(beatmap, parsedScore.passed);

    // Update user statistics (standard mode for now)
    int totalHits = parsedScore.n300 + parsedScore.n100 + parsedScore.n50 + parsedScore.nmiss;
    
    // ╔════════════════════════════════════════════════════════════════════════════════╗
    // ║  FUTURE ENHANCEMENT: RELAX & AUTOPILOT MOD HANDLING                           ║
    // ╠════════════════════════════════════════════════════════════════════════════════╣
    // ║  ANALYSIS (from bancho.py):                                                   ║
    // ║  1. Relax (4) & Autopilot (8192) are filtered out for invalid gamemode combos ║
    // ║  2. bancho.py supports LeaderboardType.Mods for separate leaderboards         ║
    // ║  3. Stats are calculated PER MOD SET, not globally                            ║
    // ║                                                                                ║
    // ║  RECOMMENDED IMPLEMENTATION:                                                  ║
    // ║  A. Create separate Leaderboard/Ranking tables indexed by (user, mods)        ║
    // ║  B. Filter scores with invalid mod combinations before stats update           ║
    // ║  C. Update stats separately for:                                              ║
    // ║     - Standard (no mods)                                                      ║
    // ║     - Hidden (512) / Hardrock (16) / Doubled (64) / Flashlight (1024)          ║
    // ║     - Relax (4) - SEPARATE RANKING                                            ║
    // ║     - Autopilot (8192) - SEPARATE RANKING                                     ║
    // ║     - Other valid combinations (HD+HR, HD+FL, etc.)                           ║
    // ║                                                                                ║
    // ║  TODO: Implement mod validation logic similar to bancho.py                   ║
    // ║  TODO: Create separate stat/ranking tables for Relax & Autopilot             ║
    // ║  TODO: Update score submission to route to correct stat handler based on mods ║
    // ║  TODO: Add UI flags for showing which leaderboard type is being viewed        ║
    // ╚════════════════════════════════════════════════════════════════════════════════╝
    
    // Only update stats with accuracy and PP if score was passed
    if (parsedScore.passed) {
      // TODO: Check if mods are Relax/Autopilot and route to separate stats handler
      // NOTE: Current implementation treats all mod combinations the same
      // FUTURE: Separate stats updates for Relax/Autopilot leaderboards
      
      statService.updateStatsAfterScoreSubmission(
          user,
          pe.nanamochi.banchus.entities.Mode.OSU, // TODO: get correct gamemode from score
          parsedScore.mapMd5,
          parsedScore.score,
          accuracy,
          parsedScore.maxCombo,
          totalHits,
          true, // TODO: determine if this is a best score
          true, // TODO: check if beatmap is ranked
          0, // TODO: calculate actual time elapsed
          null,  // No previous best score info in this simple method
          savedScore  // Pass the newly saved score to ensure it's included in calculations
      );
      logger.info("✓ User statistics updated with accuracy and PP");
      
      // TODO: Log which leaderboard type this score updated (standard/relax/autopilot)
      logger.debug("  Mods: {} | Leaderboard Type: STANDARD (TODO: implement mod-specific routing)", 
          parsedScore.mods);
    } else {
      // For failed scores, only increment playcount and total score
      // But don't update accuracy or PP
      logger.info("ℹ Score not passed, only incrementing playcount and total score");
      statService.updateStatsForFailedScore(user, parsedScore.score);
    }
  }

  /**
   * Get beatmap from local DB or fetch from osu! API v2.
   *
   * @param mapMd5 The beatmap MD5 hash
   * @return Beatmap entity or null if not found
   */
  private Beatmap getOrFetchBeatmap(String mapMd5) {
    // Try to find locally first
    Optional<Beatmap> existingOpt = beatmapRepository.findByMd5(mapMd5);
    if (existingOpt.isPresent()) {
      logger.info("✓ Beatmap found locally: {} - {} [{}]",
          existingOpt.get().getArtist(),
          existingOpt.get().getTitle(),
          existingOpt.get().getVersion());
      return existingOpt.get();
    }

    // Fetch from osu! API v2
    logger.info("🌐 Fetching beatmap from osu! API v2: {}", mapMd5);
    Beatmap beatmapFromApi = osuApiAdapter.lookupBeatmapByMd5(mapMd5);

    if (beatmapFromApi != null) {
      beatmapFromApi.setServer("osuapi");
      Beatmap saved = beatmapRepository.save(beatmapFromApi);
      logger.info("✓ Beatmap saved from API: {} - {} [{}]",
          saved.getArtist(), saved.getTitle(), saved.getVersion());
      return saved;
    }

    // Create dummy beatmap if API fails
    logger.warn("⚠️ Could not fetch from API, creating dummy beatmap");
    Beatmap dummy = Beatmap.builder()
        .md5(mapMd5)
        .artist("Unknown")
        .title("Unknown")
        .creator("Unknown")
        .version("Unknown")
        .status(0)
        .beatmapsetId(0)
        .cs(0f)
        .ar(0f)
        .od(0f)
        .hp(0f)
        .bpm(0f)
        .maxCombo(0)
        .playcount(0)
        .passcount(0)
        .lastUpdate(LocalDateTime.now())
        .server("bancho")
        .build();

    Beatmap savedDummy = beatmapRepository.save(dummy);
    logger.info("✓ Dummy beatmap saved: {}", mapMd5);
    return savedDummy;
  }

  /**
   * Save a score submission and return formatted response for client.
   * Mimics bancho.py format for compatibility with osu! client.
   *
   * @param parsedScore The parsed score data from client
   * @param username The username of the player
   * @return Formatted response string for osu! client
   */
  public String saveScoreAndReturnResponse(ParsedScore parsedScore, String username) {
    logger.info("╔══════════════════════════════════════════════════════════╗");
    logger.info("║         SCORE SUBMISSION PROCESSING                      ║");
    logger.info("╚══════════════════════════════════════════════════════════╝");
    logger.info("  Username: {}", username);
    logger.info("  Beatmap MD5: {}\n" +
        "  Score: {}\n" +
        "  PP (temp): {}\n" +
        "  Max Combo: {}\n" +
        "  Mods: {}\n" +
        "  Accuracy: {}%\n" +
        "  300s: {}, 100s: {}, 50s: {}, Misses: {}",
        parsedScore.mapMd5, parsedScore.score, parsedScore.pp, parsedScore.maxCombo,
        parsedScore.mods, String.format("%.2f", parsedScore.accuracy),
        parsedScore.n300, parsedScore.n100, parsedScore.n50, parsedScore.nmiss);

    double accuracy = parsedScore.accuracy;

    // Get or create beatmap
    Beatmap beatmap = getOrFetchBeatmap(parsedScore.mapMd5);
    if (beatmap == null) {
      logger.error("❌ Could not find or fetch beatmap for MD5: {}", parsedScore.mapMd5);
      return "error: beatmap not found";
    }

    logger.info("  ✓ Beatmap resolved: {} - {} [{}]", 
        beatmap.getArtist(), beatmap.getTitle(), beatmap.getVersion());

    // Find user
    User user = userRepository.findByUsername(username);
    if (user == null) {
      logger.error("❌ User not found: {}", username);
      return "error: user not found";
    }

    logger.info("  ✓ User found: {} (ID: {})", user.getUsername(), user.getId());

    // Calculate PP for the score
    double calculatedPP = calculateScorePP(parsedScore, beatmap);
    logger.info("  📊 Calculated PP: {}", String.format("%.2f", calculatedPP));

    // Create and save score
    Score score = Score.builder()
        .mapMd5(parsedScore.mapMd5)
        .score(parsedScore.score)
        .pp((float) calculatedPP) // Use calculated PP instead of placeholder
        .acc((float) accuracy)
        .maxCombo(parsedScore.maxCombo)
        .mods(parsedScore.mods)
        .n300(parsedScore.n300)
        .n100(parsedScore.n100)
        .n50(parsedScore.n50)
        .nmiss(parsedScore.nmiss)
        .ngeki(parsedScore.ngeki)
        .nkatu(parsedScore.nkatu)
        .perfect(parsedScore.perfect)
        .passed(parsedScore.passed)
        .status(1) // 1 = passed
        .playTime(LocalDateTime.now())
        .gameMode(parsedScore.gamemode)
        .user(user)
        .build();

    Score savedScore = scoreRepository.save(score);
    logger.info("╔══════════════════════════════════════════════════════════╗");
    logger.info("║              ✓✓✓ SCORE SAVED SUCCESSFULLY ✓✓✓           ║");
    logger.info("║  Score ID: {}                                   ║", savedScore.getId());
    logger.info("║  User: {} | Beatmap: {}  ║", username, parsedScore.mapMd5);
    logger.info("║  Score: {} | Accuracy: {} | Combo: {}    ║", 
        parsedScore.score, String.format("%.2f%%", accuracy), parsedScore.maxCombo);
    logger.info("╚══════════════════════════════════════════════════════════╝");

    // Update beatmap play/pass counts
    updateBeatmapPlayPassCounts(beatmap, parsedScore.passed);

    // Update user statistics (standard mode for now)
    int totalHits = parsedScore.n300 + parsedScore.n100 + parsedScore.n50 + parsedScore.nmiss;
    
    // ╔════════════════════════════════════════════════════════════════════════════════╗
    // ║  FUTURE ENHANCEMENT: RELAX & AUTOPILOT MOD HANDLING                           ║
    // ║  (See ScoreService.saveScore() for detailed analysis and recommendations)      ║
    // ║  TODO: Check if mods are Relax/Autopilot and route to separate stats handler  ║
    // ╚════════════════════════════════════════════════════════════════════════════════╝
    
    statService.updateStatsAfterScoreSubmission(
        user,
        pe.nanamochi.banchus.entities.Mode.OSU, // TODO: get correct gamemode from score
        parsedScore.mapMd5,
        parsedScore.score,
        accuracy,
        parsedScore.maxCombo,
        totalHits,
        true, // TODO: determine if this is a best score
        true, // TODO: check if beatmap is ranked
        0, // TODO: calculate actual time elapsed
        null,  // No previous best score info in this method
        savedScore  // Pass the newly saved score to ensure it's included in calculations
    );
    logger.info("✓ User statistics updated");
    
    // TODO: Log which leaderboard type this score updated (standard/relax/autopilot)
    logger.debug("  Mods: {} | Leaderboard Type: STANDARD (TODO: implement mod-specific routing)", 
        parsedScore.mods);

    // Build response for osu! client (simplified version)
    // Full version would include ranking charts like bancho.py
    String response = buildScoreSubmissionResponse(beatmap, savedScore, user, accuracy, parsedScore);
    logger.info("📤 Response prepared for client: {} bytes", response.length());
    
    return response;
  }

  /**
   * Build the response string for score submission
   * Mimics bancho.py format
   */
  private String buildScoreSubmissionResponse(Beatmap beatmap, Score score, User user, 
                                              double accuracy, ParsedScore parsedScore) {
    StringBuilder response = new StringBuilder();
    
    // Validate and prepare playcount/passcount to avoid integer overflow in client
    Integer playcount = beatmap.getPlaycount();
    Integer passcount = beatmap.getPasscount();
    
    // Ensure playcount is never null or 0 (minimum 1 to avoid division by zero on client)
    if (playcount == null || playcount <= 0) {
      playcount = 1;
    }
    // Ensure passcount doesn't exceed playcount and is never negative
    if (passcount == null || passcount < 0 || passcount > playcount) {
      passcount = 0;
    }
    
    // Beatmap info line
    response.append("beatmapId:").append(beatmap.getBeatmapId()).append("|")
        .append("beatmapSetId:").append(beatmap.getBeatmapsetId()).append("|")
        .append("beatmapPlaycount:").append(playcount).append("|")
        .append("beatmapPasscount:").append(passcount).append("|")
        .append("approvedDate:").append(beatmap.getLastUpdate()).append("|")
        .append("\n");
    
    // Beatmap ranking chart
    response.append("|chartId:beatmap|")
        .append("chartUrl:https://osu.ppy.sh/beatmapsets/").append(beatmap.getBeatmapsetId()).append("|")
        .append("chartName:Beatmap Ranking|")
        .append("rankBefore:|") // TODO: implement proper ranking
        .append("rankAfter:|")
        .append("rankedScoreBefore:|")
        .append("rankedScoreAfter:").append(score.getScore()).append("|")
        .append("totalScoreBefore:|")
        .append("totalScoreAfter:").append(score.getScore()).append("|")
        .append("maxComboBefore:|")
        .append("maxComboAfter:").append(score.getMaxCombo()).append("|")
        .append("accuracyBefore:|")
        .append("accuracyAfter:").append(String.format("%.2f", accuracy)).append("|")
        .append("ppBefore:|")
        .append("ppAfter:").append(score.getPp()).append("|")
        .append("onlineScoreId:").append(score.getId()).append("|")
        .append("\n");
    
    // Overall ranking chart
    response.append("|chartId:overall|")
        .append("chartUrl:https://osu.ppy.sh/u/").append(user.getId()).append("|")
        .append("chartName:Overall Ranking|")
        .append("rankBefore:|") // TODO: implement proper ranking
        .append("rankAfter:|")
        .append("rankedScoreBefore:|")
        .append("rankedScoreAfter:|")
        .append("totalScoreBefore:|")
        .append("totalScoreAfter:|")
        .append("maxComboBefore:|")
        .append("maxComboAfter:|")
        .append("accuracyBefore:|")
        .append("accuracyAfter:|")
        .append("ppBefore:|")
        .append("ppAfter:|");
    
    // No new achievements for now
    response.append("achievements-new:");
    
    return response.toString();
  }

  /**
   * Save a score submission and return both the response and score ID.
   * Replicates bancho.py logic exactly:
   * 1. Get stats BEFORE
   * 2. Get global rank BEFORE
   * 3. Fetch top 100 best scores
   * 4. Calculate weighted PP and accuracy
   * 5. Update stats
   * 6. Get global rank AFTER
   * 7. Include before/after in response
   *
   * @param parsedScore The parsed score data from client
   * @param username The username of the player
   * @return SaveScoreResult with response and score ID
   */
  public SaveScoreResult saveScoreWithResult(ParsedScore parsedScore, String username) {
    logger.info("📝 Saving score with result tracking...");
    
    // Get user
    User user = userRepository.findByUsername(username);
    if (user == null) {
      logger.error("❌ User not found: {}", username);
      return new SaveScoreResult(null, "error: user not found");
    }

    // Get beatmap
    Beatmap beatmap = getOrFetchBeatmap(parsedScore.mapMd5);
    if (beatmap == null) {
      logger.error("❌ Could not find or fetch beatmap for MD5: {}", parsedScore.mapMd5);
      return new SaveScoreResult(null, "error: beatmap not found");
    }

    // Get stats BEFORE
    Stat statBefore = statService.getStats(user, Mode.fromValue(parsedScore.gamemode));
    if (statBefore == null) {
      logger.error("❌ Stats not found for user: {}", username);
      return new SaveScoreResult(null, "error: stats not found");
    }
    
    // Create a deep copy of stats BEFORE for comparison
    Stat previousStats = new Stat();
    previousStats.setId(statBefore.getId());
    previousStats.setUser(statBefore.getUser());
    previousStats.setGamemode(statBefore.getGamemode());
    previousStats.setTotalScore(statBefore.getTotalScore());
    previousStats.setRankedScore(statBefore.getRankedScore());
    previousStats.setPerformancePoints(statBefore.getPerformancePoints());
    previousStats.setGlobalRank(statBefore.getGlobalRank());
    previousStats.setPlayCount(statBefore.getPlayCount());
    previousStats.setPlayTime(statBefore.getPlayTime());
    previousStats.setAccuracy(statBefore.getAccuracy());
    previousStats.setHighestCombo(statBefore.getHighestCombo());
    previousStats.setTotalHits(statBefore.getTotalHits());
    previousStats.setReplayViews(statBefore.getReplayViews());
    previousStats.setXhCount(statBefore.getXhCount());
    previousStats.setXCount(statBefore.getXCount());
    previousStats.setShCount(statBefore.getShCount());
    previousStats.setSCount(statBefore.getSCount());
    previousStats.setACount(statBefore.getACount());
    
    // Get global rank BEFORE
    int globalRankBefore = statService.calculateGlobalRank(user, Mode.fromValue(parsedScore.gamemode));
    
    // Get previous best score on this beatmap BEFORE saving (important!)
    List<Score> previousBests = scoreRepository.findByMapMd5AndStatusAndGameModeOrderByPpDesc(
        parsedScore.mapMd5, 2, parsedScore.gamemode);
    Score previousBestScore = previousBests.isEmpty() ? null : previousBests.get(0);
    logger.info("  Previous best score on beatmap: {}", previousBestScore != null ? previousBestScore.getPp() + "pp" : "none");
    
    // Save the score
    Score savedScore = internalSaveScore(parsedScore, username);
    if (savedScore == null) {
      logger.error("❌ Failed to save score");
      return new SaveScoreResult(null, "error: failed to save score");
    }
    
    Long scoreId = savedScore.getId();
    logger.info("✓ Score saved with ID: {}", scoreId);

    // Get stats AFTER (will be updated by internalSaveScore)
    Stat statAfter = statService.getStats(user, Mode.fromValue(parsedScore.gamemode));
    
    // Get global rank AFTER
    int globalRankAfter = statService.calculateGlobalRank(user, Mode.fromValue(parsedScore.gamemode));
    
    // Build response with BEFORE/AFTER stats
    String response = buildCompleteScoreResponse(
        savedScore, beatmap, user,
        previousStats, statAfter,
        globalRankBefore, globalRankAfter,
        previousBestScore, parsedScore
    );
    
    return new SaveScoreResult(scoreId, response);
  }

  /**
   * Internal method to save a score without generating response.
   * Used by saveScoreWithResult to save only once.
   *
   * @param parsedScore The parsed score data from client
   * @param username The username of the player
   * @return The saved Score entity or null
   */
  private Score internalSaveScore(ParsedScore parsedScore, String username) {
    logger.info("╔══════════════════════════════════════════════════════════╗");
    logger.info("║         SCORE SUBMISSION PROCESSING                      ║");
    logger.info("║  Username: {}", username);
    logger.info("║  Beatmap MD5: {}", parsedScore.mapMd5);
    logger.info("║  Score: {} | Accuracy: {}% | Combo: {}", 
        parsedScore.score, String.format("%.2f", parsedScore.accuracy), parsedScore.maxCombo);
    logger.info("║  Mods: {} | Passed: {}",
        parsedScore.mods, parsedScore.passed);
    logger.info("╚══════════════════════════════════════════════════════════╝");

    // Get or create beatmap
    Beatmap beatmap = getOrFetchBeatmap(parsedScore.mapMd5);
    if (beatmap == null) {
      logger.error("❌ Could not find or fetch beatmap for MD5: {}", parsedScore.mapMd5);
      return null;
    }

    // Find user
    User user = userRepository.findByUsername(username);
    if (user == null) {
      logger.error("❌ User not found: {}", username);
      return null;
    }

    // Check for existing best score on this beatmap by this user
    List<Score> existingBestScores = scoreRepository.findByMapMd5AndUserOrderByIdDesc(parsedScore.mapMd5, user);
    Score previousBest = null;
    if (!existingBestScores.isEmpty()) {
      // Get the BEST score from the existing ones
      for (Score s : existingBestScores) {
        if (s.getStatus() == ScoreStatus.BEST.getValue() && s.getGameMode() == parsedScore.gamemode) {
          previousBest = s;
          break;
        }
      }
    }

    // Calculate PP for the score
    double calculatedPP = calculateScorePP(parsedScore, beatmap);
    logger.info("  📊 Calculated PP: {}", String.format("%.2f", calculatedPP));

    // Check if this is a better score than the previous best
    if (previousBest != null && parsedScore.passed) {
      logger.info("  ℹ️ User {} already has a best score on this beatmap (ID: {})", 
          username, previousBest.getId());
      logger.info("     Previous: {}pp | New: {}pp", 
          String.format("%.2f", previousBest.getPp()), String.format("%.2f", calculatedPP));
      
      if (calculatedPP <= previousBest.getPp()) {
        logger.warn("  ⚠️ New score is NOT better than previous best ({} <= {}pp), marking as ATTEMPT", 
            String.format("%.2f", calculatedPP), String.format("%.2f", previousBest.getPp()));
        logger.warn("     Score will still be saved but with ATTEMPT status (not best)");
      } else {
        logger.info("  ✅ New score is BETTER! ({} > {}pp), will replace previous best", 
            String.format("%.2f", calculatedPP), String.format("%.2f", previousBest.getPp()));
      }
    }

    // Determine status based on passed flag
    int status = ScoreStatus.FAILED.getValue(); // FAILED by default
    if (parsedScore.passed) {
      status = ScoreStatus.BEST.getValue(); // BEST (will be updated later if not the best)
      // If there's a previous best and this new score is not better, mark as ATTEMPT (just another passed attempt)
      if (previousBest != null && calculatedPP <= previousBest.getPp()) {
        status = ScoreStatus.ATTEMPT.getValue(); // Not the best, just another passed attempt
      }
    }

    // Create and save score with passed flag
    Score score = Score.builder()
        .mapMd5(parsedScore.mapMd5)
        .score(parsedScore.score)
        .pp((float) calculatedPP) // Use calculated PP instead of placeholder
        .acc((float) parsedScore.accuracy)
        .maxCombo(parsedScore.maxCombo)
        .mods(parsedScore.mods)
        .n300(parsedScore.n300)
        .n100(parsedScore.n100)
        .n50(parsedScore.n50)
        .nmiss(parsedScore.nmiss)
        .ngeki(parsedScore.ngeki)
        .nkatu(parsedScore.nkatu)
        .perfect(parsedScore.perfect)
        .passed(parsedScore.passed)
        .status(status)
        .playTime(LocalDateTime.now())
        .gameMode(parsedScore.gamemode)
        .user(user)
        .build();

    Score savedScore = scoreRepository.save(score);
    
    logger.info("╔══════════════════════════════════════════════════════════╗");
    logger.info("║              ✓✓✓ SCORE SAVED SUCCESSFULLY ✓✓✓           ║");
    logger.info("║  Score ID: {} | Status: {} | Passed: {}",
        savedScore.getId(), status, parsedScore.passed);
    logger.info("║  Score: {} | Accuracy: {}% | Combo: {}",
        parsedScore.score, String.format("%.2f", parsedScore.accuracy), parsedScore.maxCombo);
    logger.info("╚══════════════════════════════════════════════════════════╝");

    // Update user statistics based on passed flag and whether it's a new best
    if (!parsedScore.passed) {
      logger.info("ℹ Score not passed, only incrementing playcount and total score");
      statService.updateStatsForFailedScore(user, parsedScore.score);
    } else if (status == ScoreStatus.BEST.getValue()) {
      // Only update stats if this is marked as BEST
      // This means it's better than any previous score on this beatmap
      logger.info("✅ Score is NEW BEST - will update player statistics");
      
      // Determine if this is a best score and if the beatmap is ranked
      boolean isBest = isNewBestScore(user, parsedScore.mapMd5, savedScore, parsedScore.gamemode);
      boolean isRanked = isBeatmapRanked(beatmap);
      
      // Get the user's previous best score on this beatmap (if exists)
      // Must be sorted by PP DESC to get the BEST, not just the first one
      Score previousBestScore = null;
      List<Score> prevScores = scoreRepository.findByMapMd5AndUserOrderByIdDesc(parsedScore.mapMd5, user);
      if (!prevScores.isEmpty()) {
        // Get the best score excluding the current one, sorted by PP descending
        List<Score> bestPrevious = prevScores.stream()
            .filter(s -> !s.getId().equals(savedScore.getId()) && 
                        s.getGameMode() == parsedScore.gamemode && 
                        s.getStatus() == 2)
            .sorted((a, b) -> Float.compare(b.getPp(), a.getPp())) // Sort by PP descending
            .toList();
        if (!bestPrevious.isEmpty()) {
          previousBestScore = bestPrevious.get(0); // Get the BEST by PP
        }
      }
      
      int totalHits = parsedScore.n300 + parsedScore.n100 + parsedScore.n50 + parsedScore.nmiss;
      statService.updateStatsAfterScoreSubmission(
          user,
          Mode.fromValue(parsedScore.gamemode),
          parsedScore.mapMd5,
          parsedScore.score,
          parsedScore.accuracy,
          parsedScore.maxCombo,
          totalHits,
          isBest,
          isRanked,
          0,
          previousBestScore,
          savedScore  // Pass the newly saved score to ensure it's included in calculations

      );
    } else {
      // Score not BEST (ATTEMPT status) - don't update stats, just record the attempt
      logger.info("⚠️ Score is NOT better than previous best - only recording as attempt, not updating stats");
      statService.updateStatsForFailedScore(user, parsedScore.score);
    }

    return savedScore;
  }

  /**
   * Build complete score response with BEFORE/AFTER stats like bancho.py.
   * Includes:
   * - Beatmap ranking chart (before/after for this score on this beatmap)
   * - Overall ranking chart (before/after for global stats)
   * - New achievements
   * 
   * Response format (EXACTLY as per bancho.py):
   * beatmapId:X|beatmapSetId:X|beatmapPlaycount:X|beatmapPasscount:X|approvedDate:X|\n
   * |chartId:beatmap|chartUrl:...|chartName:...|rankBefore:X|rankAfter:X|...|onlineScoreId:X|\n
   * |chartId:overall|chartUrl:...|chartName:...|rankBefore:X|rankAfter:X|...|ppAfter:X|
   * achievements-new:
   *
   * @param score The saved score
   * @param beatmap The beatmap
   * @param user The user
   * @param previousStats The stats BEFORE this score
   * @param currentStats The stats AFTER this score
   * @param globalRankBefore Global rank BEFORE
   * @param globalRankAfter Global rank AFTER
   * @param previousBestScore Previous best score on this beatmap (if any)
   * @param parsedScore The parsed score data
   * @return Formatted response string (EXACTLY matching bancho.py format)
   */
  private String buildCompleteScoreResponse(
      Score score, Beatmap beatmap, User user,
      Stat previousStats, Stat currentStats,
      int globalRankBefore, int globalRankAfter,
      Score previousBestScore, ParsedScore parsedScore) {
    
    StringBuilder response = new StringBuilder();

    // Beatmap info line (LINE 1 in bancho.py response)
    Integer playcount = beatmap.getPlaycount();
    Integer passcount = beatmap.getPasscount();
    if (playcount == null) playcount = 0;
    if (passcount == null) passcount = 0;
    
    response.append("beatmapId:").append(beatmap.getBeatmapId()).append("|")
        .append("beatmapSetId:").append(beatmap.getBeatmapsetId()).append("|")
        .append("beatmapPlaycount:").append(playcount).append("|")
        .append("beatmapPasscount:").append(passcount).append("|")
        .append("approvedDate:").append(beatmap.getLastUpdate()).append("|")
        .append("\n");
    
    // Beatmap ranking chart (LINE 2 in bancho.py response)
    String beatmapRankBefore = "";
    String beatmapRankAfter = "";
    int beatmapRankedScoreBefore = 0;
    int beatmapTotalScoreBefore = 0;
    int beatmapMaxComboBefore = 0;
    double beatmapAccuracyBefore = 0.0;
    int beatmapPpBefore = 0;
    
    if (previousBestScore != null) {
      beatmapRankedScoreBefore = previousBestScore.getScore();
      beatmapTotalScoreBefore = previousBestScore.getScore();
      beatmapMaxComboBefore = previousBestScore.getMaxCombo();
      beatmapAccuracyBefore = previousBestScore.getAcc();
      beatmapPpBefore = Math.round(previousBestScore.getPp());
      
      // Calculate rank BEFORE (with previous best score)
      int rankBefore = calculateBeatmapRank(user, parsedScore.mapMd5, parsedScore.gamemode);
      if (rankBefore > 0) {
        beatmapRankBefore = String.valueOf(rankBefore);
      }
    }
    
    // Calculate rank AFTER (with new score)
    int rankAfter = calculateBeatmapRank(user, parsedScore.mapMd5, parsedScore.gamemode);
    if (rankAfter > 0) {
      beatmapRankAfter = String.valueOf(rankAfter);
    }
    
    response.append("|chartId:beatmap|")
        .append("chartUrl:https://osu.ppy.sh/beatmapsets/").append(beatmap.getBeatmapsetId()).append("|")
        .append("chartName:Beatmap Ranking|")
        .append("rankBefore:").append(beatmapRankBefore).append("|")
        .append("rankAfter:").append(beatmapRankAfter).append("|")
        .append("rankedScoreBefore:").append(beatmapRankedScoreBefore).append("|")
        .append("rankedScoreAfter:").append(score.getScore()).append("|")
        .append("totalScoreBefore:").append(beatmapTotalScoreBefore).append("|")
        .append("totalScoreAfter:").append(score.getScore()).append("|")
        .append("maxComboBefore:").append(beatmapMaxComboBefore).append("|")
        .append("maxComboAfter:").append(score.getMaxCombo()).append("|")
        .append("accuracyBefore:").append(beatmapAccuracyBefore > 0 ? String.format("%.2f", beatmapAccuracyBefore) : "").append("|")
        .append("accuracyAfter:").append(String.format("%.2f", score.getAcc())).append("|")
        .append("ppBefore:").append(beatmapPpBefore > 0 ? beatmapPpBefore : "").append("|")
        .append("ppAfter:").append(Math.round(score.getPp())).append("|")
        .append("onlineScoreId:").append(score.getId()).append("|")
        .append("\n");
    
    // Overall ranking chart (LINE 3+ in bancho.py response)
    // CRITICAL: Must match EXACTLY what bancho.py produces
    response.append("|chartId:overall|")
        .append("chartUrl:https://osu.ppy.sh/u/").append(user.getId()).append("|")
        .append("chartName:Overall Ranking|")
        .append("rankBefore:").append(globalRankBefore).append("|")
        .append("rankAfter:").append(globalRankAfter).append("|")
        .append("rankedScoreBefore:").append(previousStats != null ? previousStats.getRankedScore() : 0).append("|")
        .append("rankedScoreAfter:").append(currentStats != null ? currentStats.getRankedScore() : 0).append("|")
        .append("totalScoreBefore:").append(previousStats != null ? previousStats.getTotalScore() : 0).append("|")
        .append("totalScoreAfter:").append(currentStats != null ? currentStats.getTotalScore() : 0).append("|")
        .append("maxComboBefore:").append(previousStats != null ? previousStats.getHighestCombo() : 0).append("|")
        .append("maxComboAfter:").append(currentStats != null ? currentStats.getHighestCombo() : 0).append("|")
        .append("accuracyBefore:").append(previousStats != null ? String.format("%.2f", previousStats.getAccuracy()) : "0.00").append("|")
        .append("accuracyAfter:").append(currentStats != null ? String.format("%.2f", currentStats.getAccuracy()) : "0.00").append("|")
        .append("ppBefore:").append(previousStats != null ? previousStats.getPerformancePoints() : 0).append("|")
        .append("ppAfter:").append(currentStats != null ? currentStats.getPerformancePoints() : 0).append("|");
    
    // Achievements section (NO trailing newline!)
    response.append("achievements-new:");
    
    String responseStr = response.toString();
    logger.info("✓ Response built with BEFORE/AFTER stats:");
    logger.info("  Response length: {} bytes", responseStr.length());
    if (previousStats != null && currentStats != null) {
      logger.info("  Beatmap - Rank: {} -> {}", 
          beatmapRankBefore.isEmpty() ? "N/A" : beatmapRankBefore, 
          beatmapRankAfter.isEmpty() ? "N/A" : beatmapRankAfter);
      logger.info("  Beatmap - PP: {}pp -> {}pp", beatmapPpBefore, Math.round(score.getPp()));
      logger.info("  Overall - Rank: {} -> {}", globalRankBefore, globalRankAfter);
      logger.info("  Overall - PP: {}pp -> {}pp", previousStats.getPerformancePoints(), currentStats.getPerformancePoints());
      logger.info("  Overall - Accuracy: {} -> {}", 
          String.format("%.2f%%", previousStats.getAccuracy()), 
          String.format("%.2f%%", currentStats.getAccuracy()));
    }
    
    return responseStr;
  }

  /**
   * Calculate the user's ranking position on a specific beatmap leaderboard.
   * Returns the rank (1-indexed) based on PP sorting.
   * Returns -1 if the user has no score on this beatmap.
   * 
   * @param user The user
   * @param mapMd5 The beatmap MD5
   * @param gameMode The game mode
   * @return The rank (1-indexed) or -1 if no score exists
   */
  private int calculateBeatmapRank(User user, String mapMd5, int gameMode) {
    // Get all top scores on this beatmap
    List<Score> topScores = scoreRepository.findTopScoresOnBeatmap(mapMd5, gameMode);
    
    if (topScores.isEmpty()) {
      logger.info("  No scores found on beatmap leaderboard");
      return -1;
    }

    // Find the user's rank by searching for their best score
    for (int i = 0; i < topScores.size(); i++) {
      Score score = topScores.get(i);
      if (score.getUser().getId() == user.getId()) {
        int rank = i + 1; // 1-indexed ranking
        logger.info("  User {} found at rank #{} on beatmap leaderboard", user.getUsername(), rank);
        return rank;
      }
    }

    // User not found in top scores
    logger.info("  User {} not in top scores on beatmap leaderboard", user.getUsername());
    return -1;
  }

  /**
   * Determine if a score is a BEST score (higher PP/score than previous best on same beatmap).
   * 
   * @param user The user
   * @param mapMd5 The beatmap MD5
   * @param currentScore The current score object
   * @param gameMode The game mode
   * @return true if this is a new best score, false otherwise
   */
  private boolean isNewBestScore(User user, String mapMd5, Score currentScore, int gameMode) {
    // Get all scores for THIS USER on this beatmap/mode (excluding the current one if it's already saved)
    List<Score> userScoresOnMap = scoreRepository.findByMapMd5AndUserOrderByIdDesc(mapMd5, user);
    
    // Filter by game mode and submitted status
    List<Score> relevantScores = userScoresOnMap.stream()
        .filter(s -> s.getGameMode() == gameMode && s.getStatus() == 2 && !s.getId().equals(currentScore.getId()))
        .sorted((a, b) -> Float.compare(b.getPp(), a.getPp())) // Sort by PP descending
        .toList();
    
    if (relevantScores.isEmpty()) {
      // No previous best score for this user on this map, this is the first
      logger.info("✓ This is the first submitted score by user {} on this beatmap", user.getUsername());
      return true;
    }

    // Compare with the user's previous best score on this map
    Score previousBest = relevantScores.get(0);
    boolean isBetter = currentScore.getPp() > previousBest.getPp();
    
    logger.info("  User's previous best on this map: {}pp | Current score: {}pp | Is better: {}", 
        previousBest.getPp(), currentScore.getPp(), isBetter);
    
    return isBetter;
  }

  /**
   * Determine if a beatmap is ranked or approved (awards ranked score and PP).
   * 
   * Status values in database (osu! convention):
   * 0 = Unranked
   * 1 = Ranked
   * 2 = Approved
   * 3 = Loved
   * 
   * @param beatmap The beatmap
   * @return true if beatmap status is RANKED (1), APPROVED (2), or LOVED (3)
   */
  private boolean isBeatmapRanked(Beatmap beatmap) {
    // Check if this beatmap status contributes to PP based on scoring config
    BeatmapStatus status = BeatmapStatus.fromValue(beatmap.getStatus());
    boolean contributesToPP = scoringConfig.contributesToPP(status);
    logger.info("  Beatmap status: {} ({}) | Contributes to PP: {} (configured statuses: {})", 
        beatmap.getStatus(), status.getDisplayName(), contributesToPP, 
        scoringConfig.getPpContributingStatuses());
    return contributesToPP;
  }

  /**
   * Update beatmap play/pass counts after a score submission.
   * Increments playcount by 1 and passcount by 1 if the score passed.
   *
   * @param beatmap The beatmap to update
   * @param passed Whether the score was passed
   */
  private void updateBeatmapPlayPassCounts(Beatmap beatmap, boolean passed) {
    if (beatmap == null) {
      return;
    }

    // Ensure playcount and passcount are never null
    if (beatmap.getPlaycount() == null) {
      beatmap.setPlaycount(0);
    }
    if (beatmap.getPasscount() == null) {
      beatmap.setPasscount(0);
    }

    // Increment playcount
    beatmap.setPlaycount(beatmap.getPlaycount() + 1);

    // Increment passcount if score was passed
    if (passed) {
      beatmap.setPasscount(beatmap.getPasscount() + 1);
    }

    beatmapRepository.save(beatmap);
    logger.info("✓ Beatmap stats updated: playcount={}, passcount={}", 
        beatmap.getPlaycount(), beatmap.getPasscount());
  }

  /**
   * Calculate PP for a score using the PerformancePointsService.
   * 
   * @param parsedScore The parsed score data
   * @param beatmap The beatmap
   * @return The calculated PP value (or 0 if calculation fails)
   */
  private double calculateScorePP(ParsedScore parsedScore, Beatmap beatmap) {
    try {
      logger.info("📊 Calculating PP for score on beatmap: {} [{}]", 
          beatmap.getTitle(), beatmap.getVersion());
      
      double pp = ppService.calculatePP(
          parsedScore.mapMd5,
          parsedScore.n300,
          parsedScore.n100,
          parsedScore.n50,
          parsedScore.nmiss,
          parsedScore.maxCombo,
          parsedScore.mods,
          parsedScore.accuracy,
          parsedScore.gamemode
      );
      
      logger.info("✓ PP calculated successfully: {}", String.format("%.2f", pp));
      return pp;
      
    } catch (Exception e) {
      logger.error("❌ Error calculating PP: {}", e.getMessage());
      logger.warn("⚠️ Falling back to 0 PP for this score");
      return 0.0;
    }
  }
}
