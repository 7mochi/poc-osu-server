package pe.nanamochi.banchus.utils;

import pe.nanamochi.banchus.entities.BeatmapStatus;
import pe.nanamochi.banchus.entities.WebBeatmapStatus;

/**
 * Converts between different beatmap ranked status formats.
 * 
 * osu! API v2 format (stored in database):
 * See BeatmapStatus enum for values (-2=Graveyard, -1=WIP, 0=Pending, 1=Ranked, etc)
 * 
 * Web/Leaderboard format (sent to client):
 * See WebBeatmapStatus enum for values (-1=NotSubmitted, 0=Pending, 2=Ranked, etc)
 */
public class RankedStatusConverter {

  // Deprecated: Use BeatmapStatus enum instead
  @Deprecated(forRemoval = true)
  public static final int API_GRAVEYARD = -2;
  @Deprecated(forRemoval = true)
  public static final int API_WIP = -1;
  @Deprecated(forRemoval = true)
  public static final int API_PENDING = 0;
  @Deprecated(forRemoval = true)
  public static final int API_RANKED = 1;
  @Deprecated(forRemoval = true)
  public static final int API_APPROVED = 2;
  @Deprecated(forRemoval = true)
  public static final int API_QUALIFIED = 3;
  @Deprecated(forRemoval = true)
  public static final int API_LOVED = 4;

  // Deprecated: Use WebBeatmapStatus enum instead
  @Deprecated(forRemoval = true)
  public static final int WEB_NOT_SUBMITTED = -1;
  @Deprecated(forRemoval = true)
  public static final int WEB_PENDING = 0;
  @Deprecated(forRemoval = true)
  public static final int WEB_UPDATE_AVAILABLE = 1;
  @Deprecated(forRemoval = true)
  public static final int WEB_RANKED = 2;
  @Deprecated(forRemoval = true)
  public static final int WEB_APPROVED = 3;
  @Deprecated(forRemoval = true)
  public static final int WEB_QUALIFIED = 4;
  @Deprecated(forRemoval = true)
  public static final int WEB_LOVED = 5;

  /**
   * Convert API v2 ranked status to web/leaderboard format using enums.
   * This is used when sending beatmap status to the osu! client.
   *
   * @param apiStatus The status from osu! API v2 (stored in database)
   * @return The status in web format
   */
  public static int apiToWebStatus(int apiStatus) {
    try {
      BeatmapStatus status = BeatmapStatus.fromValue(apiStatus);
      return apiToWebStatus(status);
    } catch (IllegalArgumentException e) {
      return WebBeatmapStatus.PENDING.getValue(); // Default to pending for unknown statuses
    }
  }

  /**
   * Convert BeatmapStatus enum to web/leaderboard format.
   * This is used when sending beatmap status to the osu! client.
   *
   * @param status The BeatmapStatus enum value
   * @return The status in web format (WebBeatmapStatus value)
   */
  public static int apiToWebStatus(BeatmapStatus status) {
    switch (status) {
      case GRAVEYARD:
      case WIP:
      case PENDING:
        return WebBeatmapStatus.PENDING.getValue();
      case RANKED:
        return WebBeatmapStatus.RANKED.getValue();
      case APPROVED:
        return WebBeatmapStatus.APPROVED.getValue();
      case QUALIFIED:
        return WebBeatmapStatus.QUALIFIED.getValue();
      case LOVED:
        return WebBeatmapStatus.LOVED.getValue();
      default:
        return WebBeatmapStatus.PENDING.getValue();
    }
  }

  /**
   * Get a human-readable name for an API v2 status using BeatmapStatus enum.
   *
   * @param apiStatus The status value
   * @return Human-readable status name
   */
  public static String getApiStatusName(int apiStatus) {
    try {
      BeatmapStatus status = BeatmapStatus.fromValue(apiStatus);
      return status.getDisplayName();
    } catch (IllegalArgumentException e) {
      return "Unknown";
    }
  }

  /**
   * Get a human-readable name for a web status using WebBeatmapStatus enum.
   *
   * @param webStatus The status value
   * @return Human-readable status name
   */
  public static String getWebStatusName(int webStatus) {
    try {
      WebBeatmapStatus status = WebBeatmapStatus.fromValue(webStatus);
      return status.getDisplayName();
    } catch (IllegalArgumentException e) {
      return "Unknown";
    }
  }
}
