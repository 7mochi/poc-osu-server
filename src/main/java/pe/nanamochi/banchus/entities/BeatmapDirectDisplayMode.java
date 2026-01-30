package pe.nanamochi.banchus.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BeatmapDirectDisplayMode {
  RANKED(0),
  RANKED_STRICT(1),
  PENDING(2),
  QUALIFIED(3),
  ALL(4),
  GRAVEYARD(5),
  APPROVED(6),
  RANKED_PLAYED(7),
  LOVED(8);

  private final int value;

  public static BeatmapDirectDisplayMode fromValue(int value) {
    for (BeatmapDirectDisplayMode mode : values()) {
      if (mode.value == value) {
        return mode;
      }
    }
    throw new IllegalArgumentException("Unknown BeatmapDirectDisplayMode value: " + value);
  }

  // TODO: Move this to a utility class like PrivilegesUtil
  public static int convertToOsuApiStatus(BeatmapDirectDisplayMode displayMode) {
    return switch (displayMode) {
      case ALL -> -3; // All (Shouldn't reach here)
      case GRAVEYARD -> BeatmapRankedStatus.GRAVEYARD.getValue();
      case PENDING -> BeatmapRankedStatus.PENDING.getValue();
      case RANKED, RANKED_STRICT, RANKED_PLAYED -> BeatmapRankedStatus.RANKED.getValue();
      case APPROVED -> BeatmapRankedStatus.APPROVED.getValue();
      case QUALIFIED -> BeatmapRankedStatus.QUALIFIED.getValue();
      case LOVED -> BeatmapRankedStatus.LOVED.getValue();
    };
  }
}
