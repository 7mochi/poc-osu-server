package pe.nanamochi.banchus.services;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.ScoreRepository;
import pe.nanamochi.banchus.repositories.StatRepository;

/**
 * Service for handling ranking and leaderboard calculations.
 * Responsible for:
 * - Calculating global rank (user's position in worldwide leaderboards)
 * - Calculating beatmap rank (user's position on individual beatmap leaderboards)
 * - Retrieving leaderboard data
 * - Managing rankings for different mod types (standard, relax, autopilot)
 */
@Service
public class RankingService {

  private static final Logger logger = LoggerFactory.getLogger(RankingService.class);

  @Autowired private StatRepository statRepository;

  @Autowired private ScoreRepository scoreRepository;

  /**
   * Calculate the global rank (worldwide position) of a user in a specific gamemode.
   * Rank is calculated by counting how many users have higher Performance Points.
   * This is a simple comparison-based ranking system.
   *
   * Example:
   * - User A: 1000pp
   * - User B: 800pp
   * - User C: 600pp
   * If we're calculating for User B, the rank is 2 (User A has more PP)
   *
   * @param user The user to rank
   * @param gamemode The game mode (standard, taiko, catch, mania)
   * @return The global rank (1-indexed, 1 being the best)
   */
  public int calculateGlobalRank(User user, Mode gamemode) {
    Stat userStat = statRepository.findByUserAndGamemode(user, gamemode);

    if (userStat == null) {
      logger.warn("User {} has no stats in mode {}, rank set to -1", user.getUsername(), gamemode);
      return -1; // User without statistics
    }

    // Get all stats for this gamemode
    List<Stat> allStats = statRepository.findAll();

    // Count how many users have higher PP in this gamemode
    long usersWithMorePP = allStats.stream()
        .filter(stat -> stat.getGamemode() == gamemode)
        .filter(stat -> stat.getPerformancePoints() > userStat.getPerformancePoints())
        .count();

    // Rank is the count of users with more PP + 1
    int rank = (int) usersWithMorePP + 1;

    logger.info("✓ Calculated global rank for user {} in mode {}: #{} (PP: {})",
        user.getUsername(), gamemode, rank, userStat.getPerformancePoints());

    return rank;
  }

  /**
   * Calculate the user's ranking position on a specific beatmap leaderboard.
   * Returns the rank (1-indexed) based on PP sorting for that beatmap.
   *
   * Example:
   * On a beatmap, if the scores are:
   * 1. User A: 100pp
   * 2. User B: 80pp
   * 3. User C: 60pp
   * This function would return 2 for User B
   *
   * @param user The user
   * @param mapMd5 The beatmap MD5 hash
   * @param gameMode The game mode
   * @return The rank (1-indexed) or -1 if the user has no score on this beatmap
   */
  public int calculateBeatmapRank(User user, String mapMd5, int gameMode) {
    // Get all top scores on this beatmap, sorted by PP descending
    List<Score> topScores = scoreRepository.findTopScoresOnBeatmap(mapMd5, gameMode);

    if (topScores.isEmpty()) {
      logger.info("  No scores found on beatmap leaderboard for {}", mapMd5);
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
   * Get the top scores (leaderboard) for a specific beatmap.
   * Scores are sorted by PP descending (best first).
   * Only returns submitted/completed scores (status == 2).
   *
   * @param mapMd5 The beatmap MD5 hash
   * @param gameMode The game mode
   * @param limit Maximum number of scores to return (e.g., 50 for top 50)
   * @return List of top scores, or empty list if no scores exist
   */
  public List<Score> getBeatmapLeaderboard(String mapMd5, int gameMode, int limit) {
    List<Score> allScores = scoreRepository.findTopScoresOnBeatmap(mapMd5, gameMode);

    if (allScores.isEmpty()) {
      logger.debug("No scores found on leaderboard for beatmap {}", mapMd5);
      return List.of();
    }

    // Limit results
    if (allScores.size() > limit) {
      allScores = allScores.subList(0, limit);
    }

    logger.info("Retrieved {} scores from beatmap {} leaderboard", allScores.size(), mapMd5);
    return allScores;
  }

  /**
   * Get the global leaderboard for a specific gamemode.
   * Users are ranked by Performance Points (PP) descending.
   * Only includes users with at least one submitted score.
   *
   * @param gamemode The game mode
   * @param limit Maximum number of users to return (e.g., 50 for top 50)
   * @return List of stats ranked by PP descending, or empty list if no stats exist
   */
  public List<Stat> getGlobalLeaderboard(Mode gamemode, int limit) {
    List<Stat> allStats = statRepository.findAll();

    // Filter by gamemode and sort by PP descending
    List<Stat> leaderboard = allStats.stream()
        .filter(stat -> stat.getGamemode() == gamemode)
        .filter(stat -> stat.getPerformancePoints() > 0) // Only users with PP
        .sorted((a, b) -> Integer.compare(b.getPerformancePoints(), a.getPerformancePoints()))
        .limit(limit)
        .toList();

    logger.info("Retrieved {} users from global {} leaderboard", leaderboard.size(), gamemode);
    return leaderboard;
  }

  /**
   * Update the global rank for a user after stats have been recalculated.
   * This is called internally by StatService after PP/accuracy changes.
   *
   * @param user The user
   * @param stat The stat object to update
   * @param gamemode The game mode
   */
  public void updateGlobalRank(User user, Stat stat, Mode gamemode) {
    int rank = calculateGlobalRank(user, gamemode);
    stat.setGlobalRank(rank);
    logger.info("✓ Updated global rank for user {} in mode {}: #{}", 
        user.getUsername(), gamemode, rank);
  }

  /**
   * Get the current global rank of a user without updating the stat object.
   * Useful for checking rank without side-effects.
   *
   * @param user The user
   * @param gamemode The game mode
   * @return The global rank
   */
  public int getGlobalRank(User user, Mode gamemode) {
    return calculateGlobalRank(user, gamemode);
  }

  /**
   * Get the current beatmap rank of a user without updating any objects.
   *
   * @param user The user
   * @param mapMd5 The beatmap MD5
   * @param gameMode The game mode
   * @return The beatmap rank
   */
  public int getBeatmapRank(User user, String mapMd5, int gameMode) {
    return calculateBeatmapRank(user, mapMd5, gameMode);
  }
}
