package pe.nanamochi.banchus.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Beatmap ranking status enum.
 * Uses osu! API v2 format (stored in database).
 * 
 * IMPORTANT: Scoring Logic
 * - ALL scores can be submitted on ANY beatmap (ranked, qualified, loved, pending, etc).
 * - PP is ALWAYS calculated for all submitted scores.
 * - However, player stats are ONLY updated for beatmaps with statuses in BEATMAP_PP_CONTRIBUTING_STATUSES config:
 *   - If beatmap status is configured (default: 1,2,3 = Ranked/Approved/Qualified):
 *     * Updates PP, accuracy, rankedScore, playCount, etc.
 *   - If beatmap status is NOT configured (pending, graveyard, wip, etc):
 *     * Only adds to totalScore; PP and accuracy remain unchanged.
 * 
 * Do NOT confuse with web format (used in leaderboards):
 * - Web uses different numbers (2=Ranked, 3=Approved, 4=Qualified, 5=Loved)
 * - API v2 uses: 1=Ranked, 2=Approved, 3=Qualified, 4=Loved
 * 
 * See RankedStatusConverter for conversion between formats.
 */
@Getter
@AllArgsConstructor
public enum BeatmapStatus {
  GRAVEYARD(-2, "Graveyard"),
  WIP(-1, "WIP"),
  PENDING(0, "Pending"),
  RANKED(1, "Ranked"),
  APPROVED(2, "Approved"),
  QUALIFIED(3, "Qualified"),
  LOVED(4, "Loved");

  private final int value;
  private final String displayName;

  /**
   * Get BeatmapStatus from API v2 status code.
   * 
   * @param value The API v2 status code
   * @return The corresponding BeatmapStatus
   * @throws IllegalArgumentException if value is not a valid status
   */
  public static BeatmapStatus fromValue(int value) {
    for (BeatmapStatus status : values()) {
      if (status.value == value) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown beatmap status value: " + value);
  }

  /**
   * Check if this status contributes to ranked PP.
   * By default, only Ranked and Approved contribute.
   * 
   * This can be overridden by ScoringConfig.
   * 
   * @return true if this status typically counts for PP
   */
  public boolean typicallyCountsForPP() {
    return this == RANKED || this == APPROVED || this == QUALIFIED;
  }

  /**
   * Check if this is a playable ranked status.
   * 
   * @return true if scores can be submitted on this beatmap
   */
  public boolean isPlayable() {
    return this != GRAVEYARD && this != WIP;
  }
}
