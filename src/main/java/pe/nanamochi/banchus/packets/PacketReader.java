package pe.nanamochi.banchus.packets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.*;
import pe.nanamochi.banchus.io.data.BanchoDataReader;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.packets.client.*;

@Component
public class PacketReader {

  private static final Logger logger = LoggerFactory.getLogger(PacketReader.class);

  private final IDataReader reader;

  public PacketReader() {
    this.reader = new BanchoDataReader();
  }

  public PacketReader(IDataReader reader) {
    this.reader = reader;
  }

  public Packet readPacket(InputStream stream) throws IOException {
    int packetId = reader.readUint16(stream);
    reader.readUint8(stream);
    int length = reader.readInt32(stream);
    byte[] data = stream.readNBytes(length);
    return readPacketType(packetId, new ByteArrayInputStream(data));
  }

  public Packet readPacketType(int packetId, InputStream stream) throws IOException {
    if (packetId == Packets.OSU_USER_STATUS.getId()) {
      return readUserStatus(stream);
    } else if (packetId == Packets.OSU_MESSAGE.getId()) {
      return readMessage(stream);
    } else if (packetId == Packets.OSU_EXIT.getId()) {
      return readUserExit(stream);
    } else if (packetId == Packets.OSU_STATUS_UPDATE_REQUEST.getId()) {
      return readStatusUpdateRequest(stream);
    } else if (packetId == Packets.OSU_PONG.getId()) {
      return readPong(stream);
    } else if (packetId == Packets.OSU_PRIVATE_MESSAGE.getId()) {
      return readPrivateMessage(stream);
    } else if (packetId == Packets.OSU_RECEIVE_UPDATES.getId()) {
      return readReceiveUpdates(stream);
    } else if (packetId == Packets.OSU_USER_STATS_REQUEST.getId()) {
      return readUserStatsRequest(stream);
    } else if (packetId == Packets.OSU_CHANNEL_JOIN.getId()) {
      return readChannelJoin(stream);
    } else if (packetId == Packets.OSU_CHANNEL_LEAVE.getId()) {
      return readChannelLeave(stream);
    } else if (packetId == Packets.OSU_START_SPECTATING.getId()) {
      return readStartSpectating(stream);
    } else if (packetId == Packets.OSU_STOP_SPECTATING.getId()) {
      return readStopSpectating(stream);
    } else if (packetId == Packets.OSU_SPECTATE_FRAMES.getId()) {
      return readSpectateFrames(stream);
    } else if (packetId == Packets.OSU_CANT_SPECTATE.getId()) {
      return readCantSpectate(stream);
    } else {
      logger.warn("Packet id {} not supported yet.", packetId);
      return null;
    }
  }

  public List<Packet> readPackets(byte[] data) throws IOException {
    return readPackets(new ByteArrayInputStream(data));
  }

  public List<Packet> readPackets(InputStream stream) throws IOException {
    List<Packet> packets = new ArrayList<>();
    while (stream.available() > 0) {
      packets.add(readPacket(stream));
    }
    return packets;
  }

  public ExitPacket readUserExit(InputStream stream) {
    return new ExitPacket();
  }

  public UserStatusPacket readUserStatus(InputStream stream) throws IOException {
    UserStatusPacket packet = new UserStatusPacket();
    Status action = Status.fromValue(reader.readUint8(stream));

    packet.setAction(action);
    packet.setText(reader.readString(stream));
    packet.setBeatmapChecksum(reader.readString(stream));
    packet.setMods(Mods.fromBitmask(reader.readUint32(stream)));
    packet.setMode(Mode.fromValue(reader.readUint8(stream)));
    packet.setBeatmapId(reader.readInt32(stream));

    return packet;
  }

  public MessagePacket readMessage(InputStream stream) throws IOException {
    MessagePacket packet = new MessagePacket();
    packet.setSender(reader.readString(stream));
    packet.setContent(reader.readString(stream));
    packet.setTarget(reader.readString(stream));
    packet.setSenderId(reader.readInt32(stream));
    return packet;
  }

  public StatusUpdateRequestPacket readStatusUpdateRequest(InputStream stream) {
    // TODO: idk
    return new StatusUpdateRequestPacket();
  }

  public PongPacket readPong(InputStream stream) {
    // TODO: idk
    return new PongPacket();
  }

  public PrivateMessagePacket readPrivateMessage(InputStream stream) throws IOException {
    PrivateMessagePacket packet = new PrivateMessagePacket();
    packet.setSender(reader.readString(stream));
    packet.setContent(reader.readString(stream));
    packet.setTarget(reader.readString(stream));
    packet.setSenderId(reader.readInt32(stream));
    return packet;
  }

  public ReceiveUpdatesPacket readReceiveUpdates(InputStream stream) {
    // TODO: idk
    return new ReceiveUpdatesPacket();
  }

  public UserStatsRequestPacket readUserStatsRequest(InputStream stream) {
    // TODO: idk
    return new UserStatsRequestPacket();
  }

  public ChannelJoinPacket readChannelJoin(InputStream stream) throws IOException {
    ChannelJoinPacket packet = new ChannelJoinPacket();
    packet.setName(reader.readString(stream));
    return packet;
  }

  public ChannelLeavePacket readChannelLeave(InputStream stream) throws IOException {
    ChannelLeavePacket packet = new ChannelLeavePacket();
    packet.setName(reader.readString(stream));
    return packet;
  }

  private Packet readStartSpectating(InputStream stream) throws IOException {
    StartSpectatingPacket packet = new StartSpectatingPacket();
    packet.setUserId(reader.readInt32(stream));
    return packet;
  }

  private Packet readSpectateFrames(InputStream stream) throws IOException {
    SpectateFramesPacket packet = new SpectateFramesPacket();
    ReplayFrameBundle replayFrameBundle = new ReplayFrameBundle();

    replayFrameBundle.setExtra(reader.readUint32(stream));
    int replayFrameCount = reader.readUint16(stream);

    List<ReplayFrame> replayFrames = new ArrayList<>();
    for (int i = 0; i < replayFrameCount; i++) {
      ReplayFrame frame = new ReplayFrame();
      frame.setButtonState(reader.readUint8(stream));
      frame.setTaikoByte(reader.readUint8(stream));
      frame.setX(reader.readFloat32(stream));
      frame.setY(reader.readFloat32(stream));
      frame.setTime(reader.readInt32(stream));
      replayFrames.add(frame);
    }
    replayFrameBundle.setFrames(replayFrames);
    replayFrameBundle.setAction(ReplayAction.fromValue(reader.readUint8(stream)));

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

    replayFrameBundle.setFrame(scoreFrame);
    replayFrameBundle.setSequence(reader.readUint16(stream));

    packet.setReplayFrameBundle(replayFrameBundle);
    return packet;
  }

  private Packet readStopSpectating(InputStream stream) {
    return new StopSpectatingPacket();
  }

  private Packet readCantSpectate(InputStream stream) {
    return new CantSpectatePacket();
  }
}
