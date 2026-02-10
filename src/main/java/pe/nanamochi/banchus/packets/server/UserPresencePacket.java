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
public class UserPresencePacket implements ServerPacket {
  private int userId;
  private String username;
  private int utcOffset;
  private int countryCode;
  private int permissions;
  private float latitude;
  private float longitude;
  private int globalRank;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_USER_PRESENCE;
  }

  @Override
  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writer.writeInt32(stream, userId);
    writer.writeString(stream, username);
    writer.writeUint8(stream, (byte) (utcOffset + 24));
    writer.writeUint8(stream, (byte) countryCode);
    writer.writeUint8(stream, permissions);
    writer.writeFloat32(stream, latitude);
    writer.writeFloat32(stream, longitude);
    writer.writeInt32(stream, globalRank);
  }
}
