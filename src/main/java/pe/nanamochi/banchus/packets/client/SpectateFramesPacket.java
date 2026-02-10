package pe.nanamochi.banchus.packets.client;

import java.io.IOException;
import java.io.InputStream;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.packets.ReplayFrameBundle;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.packets.core.ClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;

@Component("SpectateFramesClientPacket")
@Data
@NoArgsConstructor
public class SpectateFramesPacket implements ClientPacket {
  private ReplayFrameBundle replayFrameBundle;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_SPECTATE_FRAMES;
  }

  @Override
  public void read(IDataReader reader, InputStream stream) throws IOException {
    this.replayFrameBundle = ReplayFrameBundle.read(reader, stream);
  }
}
