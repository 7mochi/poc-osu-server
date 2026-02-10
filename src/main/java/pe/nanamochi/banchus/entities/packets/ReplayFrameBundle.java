package pe.nanamochi.banchus.entities.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.io.data.IDataWriter;

@Data
public class ReplayFrameBundle {
  private ReplayAction action;
  private List<ReplayFrame> frames;
  private ScoreFrame frame;
  private int extra;
  private int sequence;

  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writer.writeUint32(stream, extra);
    writer.writeUint16(stream, frames.size());

    for (ReplayFrame replayFrame : frames) {
      writer.writeUint8(stream, replayFrame.getButtonState());
      writer.writeUint8(stream, replayFrame.getTaikoByte());
      writer.writeFloat32(stream, replayFrame.getX());
      writer.writeFloat32(stream, replayFrame.getY());
      writer.writeInt32(stream, replayFrame.getTime());
    }

    writer.writeUint8(stream, action.getValue());

    if (frame != null) {
      frame.write(writer, stream);
    }

    writer.writeUint16(stream, (short) sequence);
  }

  public static ReplayFrameBundle read(IDataReader reader, InputStream stream) throws IOException {
    ReplayFrameBundle replayFrameBundle = new ReplayFrameBundle();

    replayFrameBundle.setExtra(reader.readUint32(stream));
    int replayFrameCount = reader.readUint16(stream);

    List<ReplayFrame> replayFrames = new ArrayList<>();
    for (int i = 0; i < replayFrameCount; i++) {
      ReplayFrame replayFrame = new ReplayFrame();
      replayFrame.setButtonState(reader.readUint8(stream));
      replayFrame.setTaikoByte(reader.readUint8(stream));
      replayFrame.setX(reader.readFloat32(stream));
      replayFrame.setY(reader.readFloat32(stream));
      replayFrame.setTime(reader.readInt32(stream));
      replayFrames.add(replayFrame);
    }
    replayFrameBundle.setFrames(replayFrames);
    replayFrameBundle.setAction(ReplayAction.fromValue(reader.readUint8(stream)));
    replayFrameBundle.setFrame(ScoreFrame.read(reader, stream));
    replayFrameBundle.setSequence(reader.readUint16(stream));

    return replayFrameBundle;
  }
}
