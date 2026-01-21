package pe.nanamochi.banchus.packets.server;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginReplyPacket implements Packet {
  private int userId;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_LOGIN_REPLY;
  }
}
