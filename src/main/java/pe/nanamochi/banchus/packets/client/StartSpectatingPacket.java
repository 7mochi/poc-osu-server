package pe.nanamochi.banchus.packets.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartSpectatingPacket implements Packet {
  private int userId;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_START_SPECTATING;
  }
}
