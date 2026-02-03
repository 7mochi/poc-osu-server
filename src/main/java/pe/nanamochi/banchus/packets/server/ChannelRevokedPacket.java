package pe.nanamochi.banchus.packets.server;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelRevokedPacket implements Packet {
  private String channelName;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_CHANNEL_REVOKED;
  }
}
