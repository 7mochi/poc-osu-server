package pe.nanamochi.banchus.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DirectDisplayMode {
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

  public static int convertToOsuApiStatus(DirectDisplayMode displayMode) {
    return switch (displayMode) {
      case ALL -> -3; // All (Shouldn't reach here)
      case GRAVEYARD -> -2; // Graveyard
      case PENDING -> 0; // Pending
      case RANKED, RANKED_STRICT, RANKED_PLAYED -> 1; // Ranked
      case APPROVED -> 2; // Approved
      case QUALIFIED -> 3; // Qualified
      case LOVED -> 4; // Loved
    };
  }
}
