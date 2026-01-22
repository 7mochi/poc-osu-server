package pe.nanamochi.banchus.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum QuitState {
  GONE(0),
  OSU_REMAINING(1),
  IRC_REMAINING(2);

  private final int value;

  public static QuitState fromValue(int value) {
    for (QuitState state : values()) {
      if (state.value == value) {
        return state;
      }
    }
    throw new IllegalArgumentException("Unknown state value: " + value);
  }
}
