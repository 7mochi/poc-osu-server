package pe.nanamochi.banchus.entities.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import lombok.Data;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.io.data.IDataWriter;

@Data
public class ScoreFrame {
  private int time;
  private int id;
  private int total300;
  private int total100;
  private int total50;
  private int totalGeki;
  private int totalKatu;
  private int totalMiss;
  private int totalScore;
  private int maxCombo;
  private int currentCombo;
  private boolean perfect;
  private int hp;
  private int tagByte;
  private boolean usingScoreV2;
  private float comboPortion;
  private float bonusPortion;

  public void write(IDataWriter writer, OutputStream stream) throws IOException {
    writer.writeInt32(stream, time);
    writer.writeUint8(stream, id);
    writer.writeUint16(stream, total300);
    writer.writeUint16(stream, total100);
    writer.writeUint16(stream, total50);
    writer.writeUint16(stream, totalGeki);
    writer.writeUint16(stream, totalKatu);
    writer.writeUint16(stream, totalMiss);
    writer.writeUint32(stream, totalScore);
    writer.writeUint16(stream, maxCombo);
    writer.writeUint16(stream, currentCombo);
    writer.writeBoolean(stream, perfect);
    writer.writeUint8(stream, hp);
    writer.writeUint8(stream, tagByte);
    writer.writeBoolean(stream, usingScoreV2);

    if (usingScoreV2) {
      writer.writeFloat64(stream, comboPortion);
      writer.writeFloat64(stream, bonusPortion);
    }
  }

  public static ScoreFrame read(IDataReader reader, InputStream stream) throws IOException {
    ScoreFrame scoreFrame = new ScoreFrame();
    scoreFrame.setTime(reader.readInt32(stream));
    scoreFrame.setId(reader.readUint8(stream));
    scoreFrame.setTotal300(reader.readUint16(stream));
    scoreFrame.setTotal100(reader.readUint16(stream));
    scoreFrame.setTotal50(reader.readUint16(stream));
    scoreFrame.setTotalGeki(reader.readUint16(stream));
    scoreFrame.setTotalKatu(reader.readUint16(stream));
    scoreFrame.setTotalMiss(reader.readUint16(stream));
    scoreFrame.setTotalScore(reader.readUint32(stream));
    scoreFrame.setMaxCombo(reader.readUint16(stream));
    scoreFrame.setCurrentCombo(reader.readUint16(stream));
    scoreFrame.setPerfect(reader.readUint8(stream) == 1);
    scoreFrame.setHp(reader.readUint8(stream));
    scoreFrame.setTagByte(reader.readUint8(stream));
    scoreFrame.setUsingScoreV2(reader.readUint8(stream) == 1);
    if (scoreFrame.isUsingScoreV2()) {
      scoreFrame.setComboPortion((float) reader.readFloat64(stream));
      scoreFrame.setBonusPortion((float) reader.readFloat64(stream));
    }
    return scoreFrame;
  }
}
