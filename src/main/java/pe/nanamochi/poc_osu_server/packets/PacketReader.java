package pe.nanamochi.poc_osu_server.packets;

import org.springframework.stereotype.Component;
import pe.nanamochi.poc_osu_server.entities.Mode;
import pe.nanamochi.poc_osu_server.entities.Mods;
import pe.nanamochi.poc_osu_server.entities.Status;
import pe.nanamochi.poc_osu_server.entities.UserStatus;
import pe.nanamochi.poc_osu_server.io.data.BanchoDataReader;
import pe.nanamochi.poc_osu_server.io.data.IDataReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class PacketReader {

    private final IDataReader reader;

    public PacketReader() {
        this.reader = new BanchoDataReader();
    }

    public PacketReader(IDataReader reader) {
        this.reader = reader;
    }

    public Object readPacket(InputStream stream) throws IOException {
        int packetId = reader.readUint16(stream);
        reader.readUint8(stream);
        int length = reader.readInt32(stream);
        byte[] data = stream.readNBytes(length);
        return readPacketType(packetId, new ByteArrayInputStream(data));
    }

    public Object readPacketType(int packetId, InputStream stream) throws IOException {
        System.out.println("Reading packet with id: " + packetId + " (" + Packets.fromId(packetId) + ")");
        if (packetId == Packets.OSU_USER_STATUS.getId()) {
            return readUserStatus(stream);
        } else if (packetId == Packets.OSU_EXIT.getId()) {
            return readUserExit(stream);
        } else if (packetId == Packets.OSU_STATUS_UPDATE_REQUEST.getId()) {
            return readStatusUpdateRequest(stream);
        } else if (packetId == Packets.OSU_PONG.getId()) {
            return readPong(stream);
        } else if (packetId == Packets.OSU_RECEIVE_UPDATES.getId()) {
            return readReceiveUpdates(stream);
        } else if (packetId == Packets.OSU_USER_STATS_REQUEST.getId()) {
            return readUserStatsRequests(stream);
        } else {
            throw new UnsupportedOperationException("Packet id " + packetId + " not supported yet.");
        }
    }

    public List<Object> readPackets(byte[] data) throws IOException {
        return readPackets(new ByteArrayInputStream(data));
    }

    public List<Object> readPackets(InputStream stream) throws IOException {
        List<Object> packets = new ArrayList<>();
        while (stream.available() > 0) {
            packets.add(readPacket(stream));
        }
        return packets;
    }

    public Object readUserExit(InputStream stream) {
        // TODO: idk
        return null;
    }

    public UserStatus readUserStatus(InputStream stream) throws IOException {
        UserStatus userStatus = new UserStatus();
        Status action = Status.fromValue(reader.readUint8(stream));

        userStatus.setAction(action);
        userStatus.setText(reader.readString(stream));
        userStatus.setBeatmapChecksum(reader.readString(stream));
        userStatus.setMods(Mods.fromBitmask(reader.readUint32(stream)));
        userStatus.setMode(Mode.fromValue(reader.readUint8(stream)));
        userStatus.setBeatmapId(reader.readInt32(stream));

        return userStatus;
    }

    public Object readStatusUpdateRequest(InputStream stream) {
        // TODO: Reload rank
        // TODO: Enqueue stats
        return null;
    }

    public Object readPong(InputStream stream) throws IOException {
        // TODO: idk
        return null;
    }

    public Object readReceiveUpdates(InputStream stream) throws IOException {
        // TODO: idk
        return null;
    }

    public Object readUserStatsRequests(InputStream stream) throws IOException {
        // TODO: idk
        return null;
    }
}
