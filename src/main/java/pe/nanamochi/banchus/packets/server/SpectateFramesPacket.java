package pe.nanamochi.banchus.packets.server;

import java.io.IOException;
import java.io.OutputStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.packets.ReplayFrame;
import pe.nanamochi.banchus.entities.packets.ReplayFrameBundle;
import pe.nanamochi.banchus.entities.packets.ScoreFrame;
import pe.nanamochi.banchus.io.data.IDataWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.core.ServerPacket;

@Component("SpectateFramesServerPacket")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpectateFramesPacket implements ServerPacket {
  private ReplayFrameBundle replayFrameBundle;

  @Override
  public Packets getPacketType() {
    return Packets.BANCHO_SPECTATE_FRAMES;
  }

  @Override
  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writer.writeUint32(stream, replayFrameBundle.getExtra());
    writer.writeUint16(stream, replayFrameBundle.getFrames().size());

    for (ReplayFrame frame : replayFrameBundle.getFrames()) {
      writer.writeUint8(stream, frame.getButtonState());
      writer.writeUint8(stream, frame.getTaikoByte());
      writer.writeFloat32(stream, frame.getX());
      writer.writeFloat32(stream, frame.getY());
      writer.writeInt32(stream, frame.getTime());
    }

    writer.writeUint8(stream, replayFrameBundle.getAction().getValue());

    ScoreFrame frame = replayFrameBundle.getFrame();
    if (frame != null) {
      frame.write(writer, stream);
    }

    writer.writeUint16(stream, (short) replayFrameBundle.getSequence());
  }
}
