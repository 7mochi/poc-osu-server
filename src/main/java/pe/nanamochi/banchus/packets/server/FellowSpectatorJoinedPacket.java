package pe.nanamochi.banchus.packets.server;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FellowSpectatorJoinedPacket implements Packet {
  private int userId;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_FELLOW_SPECTATOR_JOINED;
  }
}
