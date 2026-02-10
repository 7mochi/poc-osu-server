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
public class MatchChangeModsPacket implements ClientPacket {
  private int mods;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_MATCH_CHANGE_MODS;
  }

  @Override
  public void read(IDataReader reader, InputStream stream) throws IOException {
    this.mods = reader.readUint32(stream);
  }
}
