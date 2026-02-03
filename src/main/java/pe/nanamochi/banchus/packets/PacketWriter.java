package pe.nanamochi.banchus.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.ReplayFrame;
import pe.nanamochi.banchus.entities.ScoreFrame;
import pe.nanamochi.banchus.io.data.BanchoDataWriter;
import pe.nanamochi.banchus.io.data.IDataWriter;
import pe.nanamochi.banchus.packets.server.*;

@Component
public class PacketWriter {

  private final IDataWriter writer;

  public PacketWriter() {
    this.writer = new BanchoDataWriter();
  }

  public PacketWriter(IDataWriter writer) {
    this.writer = writer;
  }

  public void writePacket(OutputStream stream, Packet packet) throws IOException {
    switch (packet.getPacketType()) {
      case BANCHO_LOGIN_REPLY -> write(stream, (LoginReplyPacket) packet);
      case BANCHO_ANNOUNCE -> write(stream, (AnnouncePacket) packet);
      case BANCHO_PROTOCOL_NEOGITIATION -> write(stream, (ProtocolNegotiationPacket) packet);
      case BANCHO_USER_PRESENCE -> write(stream, (UserPresencePacket) packet);
      case BANCHO_USER_STATS -> write(stream, (UserStatsPacket) packet);
      case BANCHO_USER_QUIT -> write(stream, (UserQuitPacket) packet);
      case BANCHO_RESTART -> write(stream, (RestartPacket) packet);
      case BANCHO_CHANNEL_INFO_COMPLETE -> write(stream, (ChannelInfoCompletePacket) packet);
      case BANCHO_PING -> write(stream, (PingPacket) packet);
      case BANCHO_ACCOUNT_RESTRICTED -> write(stream, (AccountRestrictedPacket) packet);
      case BANCHO_TARGET_IS_SILENCED -> write(stream, (TargetIsSilencedPacket) packet);
      case BANCHO_MESSAGE -> write(stream, (MessagePacket) packet);
      case BANCHO_SILENCE_INFO -> write(stream, (SilenceInfoPacket) packet);
      case BANCHO_CHANNEL_AVAILABLE -> write(stream, (ChannelAvailablePacket) packet);
      case BANCHO_LOGIN_PERMISSIONS -> write(stream, (LoginPermissionsPacket) packet);
      case BANCHO_CHANNEL_JOIN_SUCCESS -> write(stream, (ChannelJoinSuccessPacket) packet);
      case BANCHO_CHANNEL_AVAILABLE_AUTOJOIN ->
          write(stream, (ChannelAvailableAutoJoinPacket) packet);
      case BANCHO_SPECTATOR_JOINED -> write(stream, (SpectatorJoinedPacket) packet);
      case BANCHO_SPECTATOR_LEFT -> write(stream, (SpectatorLeftPacket) packet);
      case BANCHO_FELLOW_SPECTATOR_JOINED -> write(stream, (FellowSpectatorJoinedPacket) packet);
      case BANCHO_FELLOW_SPECTATOR_LEFT -> write(stream, (FellowSpectatorLeftPacket) packet);
      case BANCHO_CHANNEL_REVOKED -> write(stream, (ChannelRevokedPacket) packet);
      case BANCHO_SPECTATE_FRAMES -> write(stream, (SpectateFramesPacket) packet);
      case BANCHO_SPECTATOR_CANT_SPECTATE -> write(stream, (SpectatorCantSpectatePacket) packet);
      default ->
          throw new UnsupportedOperationException(
              "Cannot write packet type: " + packet.getPacketType());
    }
  }

  private void write(OutputStream stream, SilenceInfoPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getSilenceLength());
    writeRawPacket(stream, Packets.BANCHO_SILENCE_INFO, buffer.toByteArray());
  }

  private void writeRawPacket(OutputStream stream, Packets packetType, byte[] data)
      throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeUint16(buffer, packetType.getId());
    writer.writeUint8(buffer, 0);
    writer.writeUint32(buffer, data.length);
    buffer.write(data);
    stream.write(buffer.toByteArray());
  }

  private void write(OutputStream stream, LoginReplyPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getUserId());
    writeRawPacket(stream, Packets.BANCHO_LOGIN_REPLY, buffer.toByteArray());
  }

  private void write(OutputStream stream, AnnouncePacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeString(buffer, packet.getMessage());
    writeRawPacket(stream, Packets.BANCHO_ANNOUNCE, buffer.toByteArray());
  }

  private void write(OutputStream stream, ProtocolNegotiationPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getProtocolVersion());
    writeRawPacket(stream, Packets.BANCHO_PROTOCOL_NEOGITIATION, buffer.toByteArray());
  }

  private void write(OutputStream stream, UserPresencePacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getUserId());
    writer.writeString(buffer, packet.getUsername());
    writer.writeUint8(buffer, (byte) (packet.getUtcOffset() + 24));
    writer.writeUint8(buffer, (byte) packet.getCountryCode());
    writer.writeUint8(buffer, packet.getPermissions());
    writer.writeFloat32(buffer, packet.getLatitude());
    writer.writeFloat32(buffer, packet.getLongitude());
    writer.writeInt32(buffer, packet.getGlobalRank());
    writeRawPacket(stream, Packets.BANCHO_USER_PRESENCE, buffer.toByteArray());
  }

  private void write(OutputStream stream, UserStatsPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getUserId());
    writer.writeUint8(buffer, packet.getAction());
    writer.writeString(buffer, packet.getInfoText());
    writer.writeString(buffer, packet.getBeatmapMd5());
    writer.writeUint32(buffer, packet.getMods());
    writer.writeUint8(buffer, packet.getGamemode().getValue());
    writer.writeInt32(buffer, packet.getBeatmapId());
    writer.writeUint64(buffer, packet.getRankedScore());
    writer.writeFloat32(buffer, packet.getAccuracy() / 100.0f);
    writer.writeUint32(buffer, packet.getPlayCount());
    writer.writeUint64(buffer, packet.getTotalScore());
    writer.writeUint32(buffer, packet.getGlobalRank());
    writer.writeUint16(buffer, packet.getPerformancePoints());
    writeRawPacket(stream, Packets.BANCHO_USER_STATS, buffer.toByteArray());
  }

  private void write(OutputStream stream, UserQuitPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getUserId());
    writer.writeUint8(buffer, packet.getState().getValue());
    writeRawPacket(stream, Packets.BANCHO_USER_QUIT, buffer.toByteArray());
  }

  private void write(OutputStream stream, RestartPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getRetryMs());
    writeRawPacket(stream, Packets.BANCHO_RESTART, buffer.toByteArray());
  }

  private void write(OutputStream stream, ChannelInfoCompletePacket packet) throws IOException {
    writeRawPacket(stream, Packets.BANCHO_CHANNEL_INFO_COMPLETE, new byte[0]);
  }

  private void write(OutputStream stream, PingPacket packet) throws IOException {
    writeRawPacket(stream, Packets.BANCHO_PING, new byte[0]);
  }

  private void write(OutputStream stream, MessagePacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeString(buffer, packet.getSender());
    writer.writeString(buffer, packet.getContent());
    writer.writeString(buffer, packet.getTarget());
    writer.writeInt32(buffer, packet.getSenderId());

    writeRawPacket(stream, Packets.BANCHO_MESSAGE, buffer.toByteArray());
  }

  private void write(OutputStream stream, TargetIsSilencedPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    write(buffer, new MessagePacket("", "", packet.getUsername(), -1));
  }

  private void write(OutputStream stream, AccountRestrictedPacket packet) throws IOException {
    writeRawPacket(stream, Packets.BANCHO_ACCOUNT_RESTRICTED, new byte[0]);
  }

  private void write(OutputStream stream, ChannelAvailablePacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeString(buffer, packet.getRealName());
    writer.writeString(buffer, packet.getTopic());
    writer.writeInt32(buffer, packet.getUserCount());

    writeRawPacket(stream, Packets.BANCHO_CHANNEL_AVAILABLE, buffer.toByteArray());
  }

  private void write(OutputStream stream, ChannelAvailableAutoJoinPacket packet)
      throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeString(buffer, packet.getRealname());
    writer.writeString(buffer, packet.getTopic());
    writer.writeInt32(buffer, packet.getUserCount());

    writeRawPacket(stream, Packets.BANCHO_CHANNEL_AVAILABLE_AUTOJOIN, buffer.toByteArray());
  }

  private void write(OutputStream stream, LoginPermissionsPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getPrivileges());
    writeRawPacket(stream, Packets.BANCHO_LOGIN_PERMISSIONS, buffer.toByteArray());
  }

  private void write(OutputStream stream, ChannelJoinSuccessPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeString(buffer, packet.getName());
    writeRawPacket(stream, Packets.BANCHO_CHANNEL_JOIN_SUCCESS, buffer.toByteArray());
  }

  private void write(OutputStream stream, SpectatorJoinedPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getUserId());
    writeRawPacket(stream, Packets.BANCHO_SPECTATOR_JOINED, buffer.toByteArray());
  }

  private void write(OutputStream stream, SpectatorLeftPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getUserId());
    writeRawPacket(stream, Packets.BANCHO_SPECTATOR_LEFT, buffer.toByteArray());
  }

  private void write(OutputStream stream, FellowSpectatorJoinedPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getUserId());
    writeRawPacket(stream, Packets.BANCHO_FELLOW_SPECTATOR_JOINED, buffer.toByteArray());
  }

  private void write(OutputStream stream, FellowSpectatorLeftPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeInt32(buffer, packet.getUserId());
    writeRawPacket(stream, Packets.BANCHO_FELLOW_SPECTATOR_LEFT, buffer.toByteArray());
  }

  private void write(OutputStream stream, ChannelRevokedPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeString(buffer, packet.getChannelName());
    writeRawPacket(stream, Packets.BANCHO_CHANNEL_REVOKED, buffer.toByteArray());
  }

  private void write(OutputStream stream, SpectateFramesPacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeUint32(buffer, packet.getReplayFrameBundle().getExtra());
    writer.writeUint16(buffer, packet.getReplayFrameBundle().getFrames().size());

    for (ReplayFrame frame : packet.getReplayFrameBundle().getFrames()) {
      writer.writeUint8(buffer, frame.getButtonState());
      writer.writeUint8(buffer, frame.getTaikoByte());
      writer.writeFloat32(buffer, frame.getX());
      writer.writeFloat32(buffer, frame.getY());
      writer.writeInt32(buffer, frame.getTime());
    }

    writer.writeUint8(buffer, packet.getReplayFrameBundle().getAction().getValue());

    ScoreFrame frame = packet.getReplayFrameBundle().getFrame();
    if (frame != null) {
      writer.writeInt32(buffer, frame.getTime());
      writer.writeUint8(buffer, frame.getId());
      writer.writeUint16(buffer, frame.getTotal300());
      writer.writeUint16(buffer, frame.getTotal100());
      writer.writeUint16(buffer, frame.getTotal50());
      writer.writeUint16(buffer, frame.getTotalGeki());
      writer.writeUint16(buffer, frame.getTotalKatu());
      writer.writeUint16(buffer, frame.getTotalMiss());
      writer.writeUint32(buffer, frame.getTotalScore());
      writer.writeUint16(buffer, frame.getMaxCombo());
      writer.writeUint16(buffer, frame.getCurrentCombo());
      writer.writeBoolean(buffer, frame.isPerfect());
      writer.writeUint8(buffer, frame.getHp());
      writer.writeUint8(buffer, frame.getTagByte());
      writer.writeBoolean(buffer, frame.isUsingScoreV2());

      if (frame.isUsingScoreV2()) {
        writer.writeFloat64(buffer, frame.getComboPortion());
        writer.writeFloat64(buffer, frame.getBonusPortion());
      }
    }

    writer.writeUint16(buffer, (short) packet.getReplayFrameBundle().getSequence());

    writeRawPacket(stream, Packets.BANCHO_SPECTATE_FRAMES, buffer.toByteArray());
  }

  private void write(OutputStream stream, SpectatorCantSpectatePacket packet) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    writer.writeUint32(buffer, packet.getUserId());
    writeRawPacket(stream, Packets.BANCHO_SPECTATOR_CANT_SPECTATE, buffer.toByteArray());
  }
}
