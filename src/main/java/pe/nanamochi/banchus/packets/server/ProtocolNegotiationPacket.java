package pe.nanamochi.banchus.packets.server;

import java.io.IOException;
import java.io.OutputStream;
import lombok.Data;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.io.data.IDataWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.core.ServerPacket;

@Component
@Data
public class ProtocolNegotiationPacket implements ServerPacket {
  private int protocolVersion;

  public ProtocolNegotiationPacket() {
    this.protocolVersion = 19;
  }

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_PROTOCOL_NEOGITIATION;
  }

  @Override
  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writer.writeInt32(stream, protocolVersion);
  }
}
