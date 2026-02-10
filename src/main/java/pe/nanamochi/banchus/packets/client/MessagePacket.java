package pe.nanamochi.banchus.packets.client;

import java.io.IOException;
import java.io.InputStream;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.packets.core.ClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;

@Component("MessageClientPacket")
@Data
@NoArgsConstructor
public class MessagePacket implements ClientPacket {
  private String sender;
  private String content;
  private String target;
  private int senderId;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_MESSAGE;
  }

  @Override
  public void read(IDataReader reader, InputStream stream) throws IOException {
    this.sender = reader.readString(stream);
    this.content = reader.readString(stream);
    this.target = reader.readString(stream);
    this.senderId = reader.readInt32(stream);
  }

  public boolean isDirectMessage() {
    return !this.target.startsWith("#");
  }
}
