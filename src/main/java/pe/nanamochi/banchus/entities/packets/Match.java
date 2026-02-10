package pe.nanamochi.banchus.entities.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.entities.commons.*;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.io.data.IDataWriter;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Match {
  private int id;
  private boolean inProgress;
  private MatchType type;
  private int mods;
  private String name;
  private String password;
  private String beatmapName;
  private int beatmapId;
  private String beatmapMd5;
  private List<MatchSlot> slots;
  private int hostId;
  private Mode mode;
  private ScoringType scoringType;
  private MatchTeamType teamType;
  private boolean freemodsEnabled;
  private int randomSeed;

  public void write(IDataWriter writer, OutputStream stream, boolean shouldSendPassword)
      throws IOException {
    writer.writeUint16(stream, id);
    writer.writeBoolean(stream, inProgress);
    writer.writeUint8(stream, MatchType.STANDARD.getValue());
    writer.writeUint32(stream, mods);
    writer.writeString(stream, name);

    if (password != null && !password.isEmpty()) {
      if (shouldSendPassword) {
        writer.writeString(stream, password);
      } else {
        stream.write(new byte[] {0x0B, 0x00});
      }
    } else {
      stream.write(new byte[] {0x00});
    }

    writer.writeString(stream, beatmapName);
    writer.writeInt32(stream, beatmapId);
    writer.writeString(stream, beatmapMd5);

    for (MatchSlot slot : slots) {
      writer.writeUint8(stream, slot.getStatus());
    }
    for (MatchSlot slot : slots) {
      writer.writeUint8(stream, slot.getTeam().getValue());
    }
    for (MatchSlot slot : slots) {
      if ((slot.getStatus() & SlotStatus.HAS_PLAYER.getValue()) != 0) {
        writer.writeInt32(stream, slot.getUserId());
      }
    }

    writer.writeInt32(stream, hostId);
    writer.writeUint8(stream, mode.getValue());
    writer.writeUint8(stream, scoringType.getValue());
    writer.writeUint8(stream, teamType.getValue());
    writer.writeBoolean(stream, freemodsEnabled);

    if (freemodsEnabled) {
      for (MatchSlot slot : slots) {
        writer.writeUint32(stream, slot.getMods());
      }
    }
    writer.writeUint32(stream, randomSeed);
  }

  public static Match read(IDataReader reader, InputStream stream) throws IOException {
    Match match = new Match();

    match.setId(reader.readUint16(stream));
    match.setInProgress(reader.readBoolean(stream));
    match.setType(MatchType.fromValue(reader.readUint8(stream)));
    match.setMods(reader.readUint32(stream));
    match.setName(reader.readString(stream));
    match.setPassword(reader.readString(stream));
    match.setBeatmapName(reader.readString(stream));
    match.setBeatmapId(reader.readInt32(stream));
    match.setBeatmapMd5(reader.readString(stream));

    List<MatchSlot> slots = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      MatchSlot slot = new MatchSlot();
      slot.setStatus(SlotStatus.toBitmask(SlotStatus.fromBitmask(reader.readUint8(stream))));
      slots.add(slot);
    }
    match.setSlots(slots);

    for (MatchSlot slot : match.getSlots()) {
      slot.setTeam(SlotTeam.fromValue(reader.readUint8(stream)));
    }

    for (MatchSlot slot : match.getSlots()) {
      if ((slot.getStatus() & SlotStatus.HAS_PLAYER.getValue()) != 0) {
        slot.setUserId(reader.readInt32(stream));
      }
    }

    match.setHostId(reader.readInt32(stream));
    match.setMode(Mode.fromValue(reader.readUint8(stream)));
    match.setScoringType(ScoringType.fromValue(reader.readUint8(stream)));
    match.setTeamType(MatchTeamType.fromValue(reader.readUint8(stream)));
    match.setFreemodsEnabled(reader.readBoolean(stream));

    if (match.isFreemodsEnabled()) {
      for (MatchSlot slot : match.getSlots()) {
        slot.setMods(Mods.toBitmask(Mods.fromBitmask(reader.readUint32(stream))));
      }
    }

    match.setRandomSeed(reader.readUint32(stream));

    return match;
  }
}
