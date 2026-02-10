package pe.nanamochi.banchus.packets.server;

import java.io.IOException;
import java.io.OutputStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.packets.QuitState;
import pe.nanamochi.banchus.io.data.IDataWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.core.ServerPacket;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuitPacket implements ServerPacket {
  private int userId;
  private QuitState state;

  @Override
  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writer.writeInt32(stream, userId);
    writer.writeUint8(stream, state.getValue());
  }

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_USER_QUIT;
  }
}
