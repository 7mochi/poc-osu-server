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

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAvailablePacket implements ServerPacket {
  private String realName;
  private String topic;
  private int userCount;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_CHANNEL_AVAILABLE;
  }

  @Override
  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writer.writeString(stream, realName);
    writer.writeString(stream, topic);
    writer.writeInt32(stream, userCount);
  }
}
