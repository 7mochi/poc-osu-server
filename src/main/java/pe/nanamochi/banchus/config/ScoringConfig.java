package pe.nanamochi.banchus.config;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.BeatmapStatus;

/**
 * Configuration for scoring system.
 * Determines which beatmap statuses contribute to user PP calculations.
 */
@Component
public class ScoringConfig {

  private static final Logger logger = LoggerFactory.getLogger(ScoringConfig.class);

  private final Set<BeatmapStatus> ppContributingStatuses;

  public ScoringConfig(
      @Value("${banchus.scoring.pp-contributing-statuses:1,2}") String ppStatusesConfig) {
    this.ppContributingStatuses = new HashSet<>();
    
    // Parse comma-separated status list
    String[] statuses = ppStatusesConfig.split(",");
    for (String status : statuses) {
      try {
        int statusCode = Integer.parseInt(status.trim());
        ppContributingStatuses.add(BeatmapStatus.fromValue(statusCode));
      } catch (Exception e) {
        logger.warn("Invalid beatmap status code in config: {}", status);
      }
    }
    
    logger.info("🎯 Scoring config loaded:");
    logger.info("   PP-contributing beatmap statuses: {}", ppContributingStatuses);
  }

  /**
   * Check if a beatmap status contributes to PP calculations.
   * 
   * @param beatmapStatus The beatmap status (API v2 format: 1=Ranked, 2=Approved, etc.)
   * @return true if this status contributes to PP, false otherwise
   */
  public boolean contributesToPP(int beatmapStatus) {
    try {
      BeatmapStatus status = BeatmapStatus.fromValue(beatmapStatus);
      return ppContributingStatuses.contains(status);
    } catch (IllegalArgumentException e) {
      logger.warn("Unknown beatmap status: {}", beatmapStatus);
      return false;
    }
  }

  /**
   * Check if a beatmap status enum contributes to PP calculations.
   * 
   * @param status The beatmap status enum
   * @return true if this status contributes to PP, false otherwise
   */
  public boolean contributesToPP(BeatmapStatus status) {
    return ppContributingStatuses.contains(status);
  }

  /**
   * Get the set of statuses that contribute to PP.
   * 
   * @return Set of beatmap statuses
   */
  public Set<BeatmapStatus> getPpContributingStatuses() {
    return new HashSet<>(ppContributingStatuses);
  }
}
