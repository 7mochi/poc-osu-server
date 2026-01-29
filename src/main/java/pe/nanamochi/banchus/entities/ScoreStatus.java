package pe.nanamochi.banchus.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Score submission status enum.
 * Represents the state of a submitted score in the ranking system.
 * 
 * - FAILED (0): Score was not completed (player failed the beatmap)
 * - ATTEMPT (1): Score was passed but is not the best score on this beatmap
 * - BEST (2): Score was passed and is the best/personal record on this beatmap
 */
@Getter
@AllArgsConstructor
public enum ScoreStatus {
  FAILED(0, "Failed"),
  ATTEMPT(1, "Attempt"),
  BEST(2, "Best");

  private final int value;
  private final String displayName;

  /**
   * Get ScoreStatus from status code.
   * 
   * @param value The status code (0=Failed, 1=Attempt, 2=Best)
   * @return The corresponding ScoreStatus
   * @throws IllegalArgumentException if value is not a valid status
   */
  public static ScoreStatus fromValue(int value) {
    for (ScoreStatus status : values()) {
      if (status.value == value) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown score status value: " + value);
  }
}
