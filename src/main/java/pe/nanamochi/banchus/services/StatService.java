package pe.nanamochi.banchus.services;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.config.ScoringConfig;
import pe.nanamochi.banchus.entities.BeatmapStatus;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.ScoreRepository;
import pe.nanamochi.banchus.repositories.StatRepository;

@Service
public class StatService {

  private static final Logger logger = LoggerFactory.getLogger(StatService.class);

  @Autowired private StatRepository statRepository;

  @Autowired private ScoreRepository scoreRepository;

  @Autowired private ScoringConfig scoringConfig;

  public List<Stat> createAllGamemodes(User user) {
    // 0 = standard
    // 1 = taiko
    // 2 = catch
    // 3 = mania
    List<Stat> stats = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Stat stat = new Stat();
      stat.setUser(user);
      stat.setGamemode(Mode.fromValue(i));
      stats.add(statRepository.save(stat));
    }
    return stats;
  }

  public Stat getStats(User user, Mode gamemode) {
    Stat stat = statRepository.findByUserAndGamemode(user, gamemode);
    if (stat != null) {
      logger.debug("📊 Retrieved stats for user {} in mode {}: totalScore={}, rankedScore={}, pp={}, acc={}, playCount={}", 
          user.getUsername(), gamemode, stat.getTotalScore(), stat.getRankedScore(), stat.getPerformancePoints(), 
          String.format("%.2f%%", stat.getAccuracy()), stat.getPlayCount());
    }
    return stat;
  }

  /**
   * Calculates the global rank of a user in a specific gamemode.
   * The rank is calculated by counting how many users have more Performance Points.
   *
   * @param user User for which to calculate the rank
   * @param gamemode Gamemode for which to calculate the rank
   * @return Global rank (player position, 1-indexed)
   */
  public int calculateGlobalRank(User user, Mode gamemode) {
    Stat userStat = getStats(user, gamemode);
    
    if (userStat == null) {
      return -1; // User without statistics
    }

    // Get all stats for this gamemode and count how many have more PP
    List<Stat> allStats = statRepository.findAll();
    long usersWithMorePP = allStats.stream()
        .filter(stat -> stat.getGamemode() == gamemode)
        .filter(stat -> stat.getPerformancePoints() > userStat.getPerformancePoints())
        .count();

    // Rank is the amount of users with more PP + 1
    return (int) usersWithMorePP + 1;
  }

  /**
   * Update user stats after a score submission.
   * Updates: totalScore, rankedScore, playCount, playTime, accuracy, highestCombo, totalHits
   *
   * Implements the osu! official calculation method:
   * - Accuracy: Weighted average of best scores with 0.95^i decay factor + bonus
   * - PP: Weighted sum with bonus
   *
   * Note: This method should be called AFTER the score has been saved to the database.
   * It will recalculate weighted PP/accuracy and ranked score based on all best scores.
   *
   * @param user User who submitted the score
   * @param gamemode Game mode (osu, taiko, etc)
   * @param mapMd5 Beatmap MD5 (to calculate rankedScore difference if improving)
   * @param scorePoints The score value
   * @param accuracy The accuracy percentage of the current score
   * @param maxCombo The max combo achieved
   * @param totalHits Total hits (300s+100s+50s+misses)
   * @param isBest Whether this is a new best score on this map
   * @param isRanked Whether the beatmap is ranked
   * @param timeElapsed Time spent in the map (seconds)
   * @param previousBestScore The user's previous best score on this beatmap (null if first submission)
   * @param newScore The newly submitted Score object (to ensure it's included in calculations)
   */
  public void updateStatsAfterScoreSubmission(
      User user,
      Mode gamemode,
      String mapMd5,
      int scorePoints,
      double accuracy,
      int maxCombo,
      int totalHits,
      boolean isBest,
      boolean isRanked,
      int timeElapsed,
      Score previousBestScore,
      Score newScore) {
    
    Stat stat = getStats(user, gamemode);
    if (stat == null) {
      return; // User without stats
    }

    // Update totalScore (always increment)
    stat.setTotalScore(stat.getTotalScore() + scorePoints);

    // Update playCount
    stat.setPlayCount(stat.getPlayCount() + 1);

    // Update playTime
    stat.setPlayTime(stat.getPlayTime() + timeElapsed);

    // Update highestCombo - track the maximum combo ever achieved
    stat.setHighestCombo(Math.max(stat.getHighestCombo(), maxCombo));

    // Update totalHits
    stat.setTotalHits(stat.getTotalHits() + totalHits);

    // Update accuracy and PP using weighted average formula (same as osu! official servers)
    // This recalculates from ALL best scores (top 100), including the newly submitted score
    // ONLY UPDATE if the beatmap is ranked/approved/loved
    // For unranked/pending/graveyard beatmaps, accuracy and PP remain unchanged
    if (isRanked) {
      logger.info("🔄 Updating weighted accuracy and PP for user {} in mode {} (beatmap is ranked)", user.getUsername(), gamemode);
      
      // Recalculate from scratch (accuracy and PP depend on the entire top 100 list of ranked maps)
      updateWeightedAccuracy(user, stat, gamemode, newScore);
      updateWeightedPP(user, stat, gamemode, newScore);
      
      // Update rankedScore based on the recalculated top 100
      if (isBest) {
        updateRankedScore(user, stat, gamemode);
      }
    } else {
      logger.info("ℹ Skipping accuracy/PP update for user {} - beatmap is not ranked/approved/loved", user.getUsername());
    }
    
    updateGlobalRank(user, stat, gamemode);

    // Save the updated stat
    logger.info("💾 Saving updated stats to database: totalScore={}, rankedScore={}, pp={}, acc={}, playCount={}", 
        stat.getTotalScore(), stat.getRankedScore(), stat.getPerformancePoints(), 
        String.format("%.2f", stat.getAccuracy()), stat.getPlayCount());
    statRepository.save(stat);
    logger.info("✓ Stats saved successfully");
  }

  /**
   * Calculate weighted accuracy based on all best scores.
   * Formula (same as official osu! servers):
   * weighted_acc = sum(acc * 0.95^i) for all best scores sorted by pp desc
   * bonus_acc = 100 / (20 * (1 - 0.95^count))
   * final_acc = (weighted_acc * bonus_acc) / 100
   *
   * @param user The user
   * @param stat The stat to update
   * @param gamemode The game mode
   * @param newScore The newly submitted score to ensure it's included in calculations
   */
  private void updateWeightedAccuracy(User user, Stat stat, Mode gamemode, Score newScore) {
    // Get all best scores for this user/mode (on ranked beatmaps only)
    List<Score> allScores = scoreRepository.findBestScoresForUserInMode((long) user.getId(), gamemode.getValue());
    
    // Filter to only beatmaps that contribute to PP based on scoring config
    allScores = filterScoresByBeatmapStatus(allScores);
    
    // Ensure the new score is included (in case of transaction/cache issues)
    if (newScore != null && newScore.getStatus() == 2) {
      boolean alreadyExists = allScores.stream()
          .anyMatch(s -> s.getId().equals(newScore.getId()));
      if (!alreadyExists && newScore.getBeatmap() != null && scoringConfig.contributesToPP(newScore.getBeatmap().getStatus())) {
        allScores.add(newScore);
      }
    }
    
    // Filter to one score per beatmap (highest PP wins)
    List<Score> bestScores = filterBestScorePerBeatmap(allScores);
    
    // Limit to top 100 (bancho.py uses exactly 100)
    if (bestScores.size() > 100) {
      bestScores = bestScores.subList(0, 100);
    }
    
    if (bestScores.isEmpty()) {
      // No best scores, set accuracy to 0
      stat.setAccuracy(0f);
      logger.info("✓ User {} has no best scores on ranked beatmaps, accuracy set to 0", user.getUsername());
      return;
    }

    logger.info("📊 Calculating weighted accuracy for user {} in mode {}", user.getUsername(), gamemode);
    logger.info("   Total best scores found: {}", bestScores.size());

    // Calculate weighted accuracy
    double weightedAcc = 0.0;
    for (int i = 0; i < bestScores.size(); i++) {
      double weight = Math.pow(0.95, i);
      double scoreAcc = bestScores.get(i).getAcc();
      weightedAcc += scoreAcc * weight;
      if (i < 5) {
        logger.debug("   Score {}: acc={}, weight={}, contribution={}",
            i + 1, String.format("%.2f%%", scoreAcc), String.format("%.6f", weight), String.format("%.2f", scoreAcc * weight));
      }
    }

    // Calculate bonus
    int scoreCount = bestScores.size();
    double bonusAcc = 100.0 / (20 * (1 - Math.pow(0.95, scoreCount)));

    // Final accuracy calculation
    double finalAccuracy = (weightedAcc * bonusAcc) / 100.0;

    // Clamp between 0 and 100
    finalAccuracy = Math.max(0, Math.min(100, finalAccuracy));

    stat.setAccuracy((float) finalAccuracy);
    
    logger.info("✓ Updated weighted accuracy for user {} in mode {}: {} (weighted={}, bonus={}, count={})",
        user.getUsername(), gamemode, String.format("%.2f%%", finalAccuracy), 
        String.format("%.2f", weightedAcc), String.format("%.6f", bonusAcc), scoreCount);
  }

  /**
   * Calculate weighted PP based on all best scores.
   * Formula (same as official osu! servers):
   * weighted_pp = sum(pp * 0.95^i) for all best scores sorted by pp desc
   * bonus_pp = 416.6667 * (1 - 0.9994^count)
   * final_pp = round(weighted_pp + bonus_pp)
   *
   * @param user The user
   * @param stat The stat to update
   * @param gamemode The game mode
   * @param newScore The newly submitted score to ensure it's included in calculations
   */
  private void updateWeightedPP(User user, Stat stat, Mode gamemode, Score newScore) {
    // Get all best scores for this user/mode (on ranked beatmaps only)
    List<Score> allScores = scoreRepository.findBestScoresForUserInMode((long) user.getId(), gamemode.getValue());
    
    // Filter to only beatmaps that contribute to PP based on scoring config
    allScores = filterScoresByBeatmapStatus(allScores);
    
    // Ensure the new score is included (in case of transaction/cache issues)
    if (newScore != null && newScore.getStatus() == 2) {
      boolean alreadyExists = allScores.stream()
          .anyMatch(s -> s.getId().equals(newScore.getId()));
      if (!alreadyExists && newScore.getBeatmap() != null && scoringConfig.contributesToPP(newScore.getBeatmap().getStatus())) {
        allScores.add(newScore);
      }
    }
    
    // Filter to one score per beatmap (highest PP wins)
    List<Score> bestScores = filterBestScorePerBeatmap(allScores);
    
    // Limit to top 100 (bancho.py uses exactly 100)
    if (bestScores.size() > 100) {
      bestScores = bestScores.subList(0, 100);
    }
    
    if (bestScores.isEmpty()) {
      // No best scores, set PP to 0
      stat.setPerformancePoints(0);
      logger.info("✓ User {} has no best scores on ranked beatmaps, PP set to 0", user.getUsername());
      return;
    }

    logger.info("📊 Calculating weighted PP for user {} in mode {}", user.getUsername(), gamemode);
    logger.info("   Total best scores found: {}", bestScores.size());

    // Calculate weighted PP
    double weightedPp = 0.0;
    for (int i = 0; i < bestScores.size(); i++) {
      double weight = Math.pow(0.95, i);
      double scorePp = bestScores.get(i).getPp();
      weightedPp += scorePp * weight;
      if (i < 5) {
        logger.debug("   Score {}: pp={}, weight={}, contribution={}",
            i + 1, String.format("%.2f", scorePp), String.format("%.6f", weight), String.format("%.2f", scorePp * weight));
      }
    }

    // Calculate bonus PP
    int scoreCount = bestScores.size();
    double bonusPp = 416.6667 * (1 - Math.pow(0.9994, scoreCount));

    // Final PP calculation
    double finalPp = weightedPp + bonusPp;

    // Round to nearest integer
    int finalPpInt = (int) Math.round(finalPp);

    // Ensure PP is never negative
    finalPpInt = Math.max(0, finalPpInt);

    stat.setPerformancePoints(finalPpInt);
    
    logger.info("✓ Updated weighted PP for user {} in mode {}: {}pp (weighted={}, bonus={}, count={})",
        user.getUsername(), gamemode, finalPpInt, 
        String.format("%.2f", weightedPp), String.format("%.2f", bonusPp), scoreCount);
  }

  /**
   * Calculate and update the ranked score based on all best scores on ranked beatmaps.
   * RankedScore = sum of the score values of the user's top 100 best scores on ranked/approved beatmaps.
   * This is recalculated from scratch every time a new score is submitted, to ensure accuracy.
   *
   * @param user The user
   * @param stat The stat to update
   * @param gamemode The game mode
   */
  private void updateRankedScore(User user, Stat stat, Mode gamemode) {
    // Get all best scores for this user/mode (on ranked beatmaps only)
    List<Score> allScores = scoreRepository.findBestScoresForUserInMode((long) user.getId(), gamemode.getValue());
    
    // Filter to one score per beatmap (highest PP wins)
    List<Score> bestScores = filterBestScorePerBeatmap(allScores);
    
    // Limit to top 100
    if (bestScores.size() > 100) {
      bestScores = bestScores.subList(0, 100);
    }
    
    // Calculate ranked score as sum of all best score values
    long rankedScore = 0L;
    for (Score score : bestScores) {
      rankedScore += score.getScore();
    }
    
    stat.setRankedScore((int) rankedScore);
    
    logger.info("✓ Updated ranked score for user {} in mode {}: {} (based on {} best scores)",
        user.getUsername(), gamemode, rankedScore, bestScores.size());
  }

  /**
   * Calculate and update the global rank for a user in a specific gamemode.
   * Rank is calculated by counting how many users have more Performance Points.
   *
   * @param user The user
   * @param stat The stat to update
   * @param gamemode The game mode
   */
  private void updateGlobalRank(User user, Stat stat, Mode gamemode) {
    // Get all stats for this gamemode
    List<Stat> allStats = statRepository.findAll();
    
    // Count how many users have more PP in this gamemode
    long usersWithMorePP = allStats.stream()
        .filter(s -> s.getGamemode() == gamemode)
        .filter(s -> s.getPerformancePoints() > stat.getPerformancePoints())
        .count();

    // Rank is the count of users with more PP + 1
    int rank = (int) usersWithMorePP + 1;
    
    stat.setGlobalRank(rank);
    
    logger.info("✓ Updated global rank for user {} in mode {}: #{} (PP: {})",
        user.getUsername(), gamemode, rank, stat.getPerformancePoints());
  }

  /**
   * Update user statistics for a FAILED score (not completed).
   * Only increments playCount and totalScore, but does NOT update accuracy or PP.
   *
   * @param user User whose stats should be updated
   * @param scorePoints Points scored
   */
  public void updateStatsForFailedScore(User user, int scorePoints) {
    // Update stats for all gamemodes (simplified - assuming standard mode for now)
    Stat stat = getStats(user, Mode.OSU);
    if (stat == null) {
      return; // User without stats
    }

    // Only increment playCount (attempts)
    stat.setPlayCount(stat.getPlayCount() + 1);

    // Do NOT update accuracy, PP, rankedScore, or totalScore for failed attempts
    // Only update attempts counter

    statRepository.save(stat);
  }

  /**
   * Filter scores to get the best (highest PP) score for each beatmap.
   * This ensures we don't count multiple scores on the same beatmap towards accuracy/PP calculations.
   * After filtering, re-sorts by PP descending to maintain the correct order for weighted calculations.
   * 
   * @param scores All scores to filter
   * @return Filtered list with one score per beatmap, sorted by PP descending
   */
  private List<Score> filterBestScorePerBeatmap(List<Score> scores) {
    java.util.Map<String, Score> bestPerBeatmap = new java.util.LinkedHashMap<>();
    
    // Iterate through scores and keep only the first (highest PP) occurrence of each beatmap
    for (Score score : scores) {
      bestPerBeatmap.putIfAbsent(score.getMapMd5(), score);
    }
    
    // Convert to list and sort by PP descending
    List<Score> result = new java.util.ArrayList<>(bestPerBeatmap.values());
    result.sort((a, b) -> Float.compare(b.getPp(), a.getPp()));
    
    return result;
  }

  /**
   * Filter scores to only include those on beatmaps that contribute to PP.
   * Uses the ScoringConfig to determine which beatmap statuses count.
   * 
   * @param scores The scores to filter
   * @return Filtered list containing only scores on beatmaps that contribute to PP
   */
  private List<Score> filterScoresByBeatmapStatus(List<Score> scores) {
    return scores.stream()
        .filter(score -> {
          if (score.getBeatmap() == null) {
            logger.debug("Score {} has no beatmap object, skipping", score.getId());
            return false;
          }
          int status = score.getBeatmap().getStatus();
          boolean contributes = scoringConfig.contributesToPP(status);
          if (!contributes) {
            logger.debug("Score {} on beatmap status {} doesn't contribute to PP, filtering out", score.getId(), status);
          }
          return contributes;
        })
        .toList();
  }

}
