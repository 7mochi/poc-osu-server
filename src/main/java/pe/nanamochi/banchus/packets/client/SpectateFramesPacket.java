package pe.nanamochi.banchus.packets.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.entities.ReplayFrameBundle;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpectateFramesPacket implements Packet {
  private ReplayFrameBundle replayFrameBundle;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_SPECTATE_FRAMES;
  }
}
