package pe.nanamochi.poc_osu_server.packets;

import org.springframework.stereotype.Component;
import pe.nanamochi.poc_osu_server.entities.CountryCode;
import pe.nanamochi.poc_osu_server.entities.db.Session;
import pe.nanamochi.poc_osu_server.entities.db.Stat;
import pe.nanamochi.poc_osu_server.entities.db.User;
import pe.nanamochi.poc_osu_server.io.data.BanchoDataWriter;
import pe.nanamochi.poc_osu_server.io.data.IDataWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@Component
public class PacketHandler {

    private final IDataWriter writer;

    public PacketHandler() {
        this.writer = new BanchoDataWriter();
    }

    public PacketHandler(IDataWriter writer) {
        this.writer = writer;
    }

    public void writePacket(OutputStream stream, int packetId, byte[] data) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeUint16(buffer, packetId);
        writer.writeUint8(buffer, 0);
        writer.writeUint32(buffer, data.length);
        buffer.write(data);
        stream.write(buffer.toByteArray());
    }

    public void writePacket(OutputStream stream, Packets packet, byte[] data) throws IOException {
        writePacket(stream, packet.getId(), data);
    }

    public void writeUserStats(OutputStream stream, Session session, Stat stat) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, stat.getId());
        writer.writeUint8(buffer, session.getAction());
        writer.writeString(buffer, session.getInfoText());
        writer.writeString(buffer, session.getBeatmapMd5());
        writer.writeUint32(buffer, session.getMods());
        writer.writeUint8(buffer, session.getGamemode());
        writer.writeInt32(buffer, session.getBeatmapId());
        writer.writeUint64(buffer, stat.getRankedScore());
        writer.writeFloat32(buffer, stat.getAccuracy());
        writer.writeUint32(buffer, stat.getPlayCount());
        writer.writeUint64(buffer, stat.getTotalScore());
        writer.writeUint32(buffer, 727); // TODO: global rank
        writer.writeUint16(buffer, stat.getPerformancePoints());
        writePacket(stream, Packets.BANCHO_USER_STATS.getId(), buffer.toByteArray());
    }

    public void writeAnnouncement(OutputStream stream, String message) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeString(buffer, message);
        writePacket(stream, Packets.BANCHO_ANNOUNCE.getId(), buffer.toByteArray());
    }

    public void writeProtocolNegotiation(OutputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, 19);
        writePacket(stream, Packets.BANCHO_PROTOCOL_NEOGITIATION.getId(), buffer.toByteArray());
    }

    public void writeUserPresence(OutputStream stream, User user, Session session) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, user.getId());
        writer.writeString(buffer, user.getUsername());
        writer.writeUint8(buffer, (byte) (session.getUtcOffset() + 24));
        writer.writeUint8(buffer, (byte) CountryCode.fromCode(session.getCountry()).id());
        writer.writeUint8(buffer, 0); // TODO: permissions
        writer.writeFloat32(buffer, session.getLatitude());
        writer.writeFloat32(buffer, session.getLongitude());
        writer.writeInt32(buffer, 727); // TODO: global rank
        writePacket(stream, Packets.BANCHO_USER_PRESENCE.getId(), buffer.toByteArray());
    }

    public void writeRestart(OutputStream stream, int retryMs) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, retryMs);
        writePacket(stream, Packets.BANCHO_RESTART.getId(), buffer.toByteArray());
    }

    public void writeChannelInfoComplete(OutputStream stream) throws IOException {
        writePacket(stream, Packets.BANCHO_CHANNEL_INFO_COMPLETE.getId(), new ByteArrayOutputStream().toByteArray());
    }

    public void writeLoginReply(OutputStream stream, int reply) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, reply);
        writePacket(stream, Packets.BANCHO_LOGIN_REPLY.getId(), buffer.toByteArray());
    }


}
