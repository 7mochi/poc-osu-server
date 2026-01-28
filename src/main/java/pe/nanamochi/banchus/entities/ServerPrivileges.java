package pe.nanamochi.banchus.entities;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ServerPrivileges {
  UNRESTRICTED(1 << 0),
  SUBMITTED_HARDWARE_IDENTITY(1 << 1),
  SUPPORTER(1 << 4),
  PREMIUM(1 << 5),
  BEATMAP_NOMINATOR(1 << 7),
  CHAT_MODERATOR(1 << 9),
  MULTIPLAYER_STAFF(1 << 11),
  ACCOUNT_MANAGEMENT(1 << 13),
  SUPER_ADMIN(1 << 30);

  private final int value;

  public static int toBitmask(List<ServerPrivileges> privileges) {
    int bitmask = 0;
    for (ServerPrivileges privilege : privileges) {
      bitmask |= privilege.getValue();
    }
    return bitmask;
  }
}
