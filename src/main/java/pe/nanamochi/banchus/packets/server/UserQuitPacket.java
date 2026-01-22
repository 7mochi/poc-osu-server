package pe.nanamochi.banchus.packets.server;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.entities.QuitState;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuitPacket implements Packet {
  private int userId;
  private QuitState state;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_USER_QUIT;
  }
}
