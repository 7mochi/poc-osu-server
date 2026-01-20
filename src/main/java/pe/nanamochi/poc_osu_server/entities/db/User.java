package pe.nanamochi.poc_osu_server.entities.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import pe.nanamochi.poc_osu_server.io.IBanchoIO;
import pe.nanamochi.poc_osu_server.io.data.BanchoDataReader;
import pe.nanamochi.poc_osu_server.io.data.BanchoDataWriter;
import pe.nanamochi.poc_osu_server.io.data.IDataReader;
import pe.nanamochi.poc_osu_server.io.data.IDataWriter;
import pe.nanamochi.poc_osu_server.packets.Packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@Entity
@Data
@Table(name = "users")
public class User implements IBanchoIO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("3")
    private int id;
    private String username;
    private String email;
    private String passwordMd5;
    private int country;
    private int privileges;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Transient
    protected final IDataReader reader;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Transient
    protected final IDataWriter writer;

    public User() {
        this.reader = new BanchoDataReader();
        this.writer = new BanchoDataWriter();
    }

    public String getAvatarFilename() {
        return this.getId() + "_000.png";
    }

    @Override
    public void writePacket(OutputStream stream, int packetId, byte[] data) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeUint16(buffer, packetId);
        writer.writeUint8(buffer, 0);
        writer.writeUint32(buffer, data.length);
        buffer.write(data);
        stream.write(buffer.toByteArray());
    }

    @Override
    public Object readPacket(InputStream stream) throws IOException {
        return null;
    }

    @Override
    public void writeLoginReply(OutputStream stream, int reply) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, reply);
        writePacket(stream, Packets.BANCHO_LOGIN_REPLY.getId(), buffer.toByteArray());
    }

    @Override
    public void writePing(OutputStream stream) throws IOException {

    }

    @Override
    public void writeIrcChangeUsername(OutputStream stream, String oldName, String newName) throws IOException {

    }

    @Override
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

    @Override
    public void writeSpectatorJoined(OutputStream stream, int userId) throws IOException {

    }

    @Override
    public void writeSpectatorLeft(OutputStream stream, int userId) throws IOException {

    }

    @Override
    public void writeVersionUpdate(OutputStream stream) throws IOException {

    }

    @Override
    public void writeSpectatorCantSpectate(OutputStream stream, int userId) throws IOException {

    }

    @Override
    public void writeGetAttention(OutputStream stream) throws IOException {

    }

    @Override
    public void writeAnnouncement(OutputStream stream, String message) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeString(buffer, message);
        writePacket(stream, Packets.BANCHO_ANNOUNCE.getId(), buffer.toByteArray());
    }

    @Override
    public void writeMatchDisband(OutputStream stream, int matchId) throws IOException {

    }

    @Override
    public void writeLobbyJoin(OutputStream stream, int userId) throws IOException {

    }

    @Override
    public void writeLobbyPart(OutputStream stream, int userId) throws IOException {

    }

    @Override
    public void writeMatchJoinFail(OutputStream stream) throws IOException {

    }

    @Override
    public void writeFellowSpectatorJoined(OutputStream stream, int userId) throws IOException {

    }

    @Override
    public void writeFellowSpectatorLeft(OutputStream stream, int userId) throws IOException {

    }

    @Override
    public void writeMatchTransferHost(OutputStream stream) throws IOException {

    }

    @Override
    public void writeMatchAllPlayersLoaded(OutputStream stream) throws IOException {

    }

    @Override
    public void writeMatchPlayerFailed(OutputStream stream, long slotId) throws IOException {

    }

    @Override
    public void writeMatchComplete(OutputStream stream) throws IOException {

    }

    @Override
    public void writeMatchSkip(OutputStream stream) throws IOException {

    }

    @Override
    public void writeUnauthorized(OutputStream stream) throws IOException {

    }

    @Override
    public void writeChannelJoinSuccess(OutputStream stream, String channel) throws IOException {

    }

    @Override
    public void writeChannelRevoked(OutputStream stream, String channel) throws IOException {

    }

    @Override
    public void writeLoginPermissions(OutputStream stream, long permissions) throws IOException {

    }

    @Override
    public void writeFriendsList(OutputStream stream, List<Integer> userIds) throws IOException {

    }

    @Override
    public void writeProtocolNegotiation(OutputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, 19);
        writePacket(stream, Packets.BANCHO_PROTOCOL_NEOGITIATION.getId(), buffer.toByteArray());
    }

    @Override
    public void writeMonitor(OutputStream stream) throws IOException {

    }

    @Override
    public void writeMatchPlayerSkipped(OutputStream stream, int slotId) throws IOException {

    }

    @Override
    public void writeUserPresence(OutputStream stream, Session session) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, this.id);
        writer.writeString(buffer, this.username);
        writer.writeUint8(buffer, (byte) (session.getUtcOffset() + 24));
        writer.writeUint8(buffer, (byte) this.country);
        writer.writeUint8(buffer, 0); // TODO: permissions
        writer.writeFloat32(buffer, session.getLatitude());
        writer.writeFloat32(buffer, session.getLongitude());
        writer.writeInt32(buffer, 727); // TODO: global rank
        writePacket(stream, Packets.BANCHO_USER_PRESENCE.getId(), buffer.toByteArray());
    }

    @Override
    public void writeRestart(OutputStream stream, int retryMs) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeInt32(buffer, retryMs);
        writePacket(stream, Packets.BANCHO_RESTART.getId(), buffer.toByteArray());
    }

    @Override
    public void writeChannelInfoComplete(OutputStream stream) throws IOException {
        writePacket(stream, Packets.BANCHO_CHANNEL_INFO_COMPLETE.getId(), new ByteArrayOutputStream().toByteArray());
    }

    @Override
    public void writeMatchChangePassword(OutputStream stream, String password) throws IOException {

    }

    @Override
    public void writeSilenceInfo(OutputStream stream, int timeRemaining) throws IOException {

    }

    @Override
    public void writeUserSilenced(OutputStream stream, long userId) throws IOException {

    }

    @Override
    public void writeUserDMsBlocked(OutputStream stream, String targetName) throws IOException {

    }

    @Override
    public void writeTargetIsSilenced(OutputStream stream, String targetName) throws IOException {

    }

    @Override
    public void writeVersionUpdateForced(OutputStream stream) throws IOException {

    }

    @Override
    public void writeSwitchServer(OutputStream stream, int target) throws IOException {

    }

    @Override
    public void writeAccountRestricted(OutputStream stream) throws IOException {

    }

    @Override
    public void writeRTX(OutputStream stream, String message) throws IOException {

    }

    @Override
    public void writeMatchAbort(OutputStream stream) throws IOException {

    }

    @Override
    public void writeSwitchTournamentServer(OutputStream stream, String ip) throws IOException {

    }
}
