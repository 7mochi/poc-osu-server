package pe.nanamochi.banchus.packets.client;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsRequestPacket implements Packet {
  private List<Integer> userIds;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_USER_STATS_REQUEST;
  }
}
