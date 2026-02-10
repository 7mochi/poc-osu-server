package pe.nanamochi.banchus.packets.server;

import java.io.IOException;
import java.io.OutputStream;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.io.data.IDataWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.core.ServerPacket;

@Component
@Data
@NoArgsConstructor
public class PingPacket implements ServerPacket {
  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_PING;
  }

  @Override
  public void write(IDataWriter writer, OutputStream stream) throws IOException {}
}
