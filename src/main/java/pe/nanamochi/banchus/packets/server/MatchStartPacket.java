package pe.nanamochi.banchus.packets.server;

import java.io.IOException;
import java.io.OutputStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.packets.Match;
import pe.nanamochi.banchus.io.data.IDataWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.core.ServerPacket;

@Component("MatchStartServerPacket")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchStartPacket implements ServerPacket {
  private Match match;
  private boolean shouldSendPassword;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_MATCH_START;
  }

  @Override
  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writeMatch(writer, stream, match, shouldSendPassword);
  }

  private void writeMatch(
      IDataWriter writer, OutputStream stream, Match match, boolean shouldSendPassword)
      throws IOException {
    match.write(writer, stream, shouldSendPassword);
  }
}
