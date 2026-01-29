package pe.nanamochi.banchus.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Beatmap ranking status enum for web/leaderboard format.
 * Used when sending beatmap status to the osu! client via leaderboard protocol.
 * 
 * Web/Leaderboard format (sent to client):
 * - NOT_SUBMITTED = -1
 * - PENDING = 0
 * - UPDATE_AVAILABLE = 1
 * - RANKED = 2
 * - APPROVED = 3
 * - QUALIFIED = 4
 * - LOVED = 5
 * 
 * Do NOT confuse with API v2 format (stored in database):
 * - API v2 uses: -2=Graveyard, -1=WIP, 0=Pending, 1=Ranked, 2=Approved, 3=Qualified, 4=Loved
 * 
 * See RankedStatusConverter for conversion between formats.
 */
@Getter
@AllArgsConstructor
public enum WebBeatmapStatus {
  NOT_SUBMITTED(-1, "Not Submitted"),
  PENDING(0, "Pending"),
  UPDATE_AVAILABLE(1, "Update Available"),
  RANKED(2, "Ranked"),
  APPROVED(3, "Approved"),
  QUALIFIED(4, "Qualified"),
  LOVED(5, "Loved");

  private final int value;
  private final String displayName;

  /**
   * Get WebBeatmapStatus from web status code.
   * 
   * @param value The web status code
   * @return The corresponding WebBeatmapStatus
   * @throws IllegalArgumentException if value is not a valid status
   */
  public static WebBeatmapStatus fromValue(int value) {
    for (WebBeatmapStatus status : values()) {
      if (status.value == value) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown web beatmap status value: " + value);
  }

  /**
   * Check if this status is a playable ranked status.
   * 
   * @return true if scores can be viewed/played on this beatmap
   */
  public boolean isRanked() {
    return this == RANKED || this == APPROVED || this == QUALIFIED || this == LOVED;
  }
}
