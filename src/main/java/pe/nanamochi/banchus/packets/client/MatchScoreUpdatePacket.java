package pe.nanamochi.banchus.packets.client;

import java.io.IOException;
import java.io.InputStream;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.packets.ScoreFrame;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.packets.core.ClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;

@Component("MatchScoreUpdateClientPacket")
@Data
@NoArgsConstructor
public class MatchScoreUpdatePacket implements ClientPacket {
  private ScoreFrame frame;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_MATCH_SCORE_UPDATE;
  }

  @Override
  public void read(IDataReader reader, InputStream stream) throws IOException {
    this.frame = ScoreFrame.read(reader, stream);
  }
}
