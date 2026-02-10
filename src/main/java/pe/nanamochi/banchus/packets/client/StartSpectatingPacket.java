package pe.nanamochi.banchus.packets.client;

import java.io.IOException;
import java.io.InputStream;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.packets.core.ClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;

@Component
@Data
@NoArgsConstructor
public class StartSpectatingPacket implements ClientPacket {
  private int userId;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_START_SPECTATING;
  }

  @Override
  public void read(IDataReader reader, InputStream stream) throws IOException {
    this.userId = reader.readInt32(stream);
  }
}
