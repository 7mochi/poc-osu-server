package pe.nanamochi.banchus.packets.server;

import java.io.IOException;
import java.io.OutputStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.io.data.IDataWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.core.ServerPacket;

@Component("MessageServerPacket")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagePacket implements ServerPacket {
  private String sender;
  private String content;
  private String target;
  private int senderId;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_MESSAGE;
  }

  @Override
  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writer.writeString(stream, sender);
    writer.writeString(stream, content);
    writer.writeString(stream, target);
    writer.writeInt32(stream, senderId);
  }
}
