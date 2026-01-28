package pe.nanamochi.banchus.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ClientPrivileges {
  PLAYER(1),
  MODERATOR(1 << 1),
  SUPPORTER(1 << 2),
  OWNER(1 << 3),
  DEVELOPER(1 << 4),
  TOURNAMENT(1 << 5); // Note; not used in communications with osu! client

  private final int value;
}
