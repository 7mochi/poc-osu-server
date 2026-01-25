package pe.nanamochi.banchus.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.springframework.stereotype.Component;
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
    writer.writeFloat32(buffer, packet.getAccuracy());
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
}
