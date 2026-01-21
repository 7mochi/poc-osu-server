package pe.nanamochi.banchus.packets;

import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.*;
import pe.nanamochi.banchus.io.data.BanchoDataReader;
import pe.nanamochi.banchus.io.data.IDataReader;
import pe.nanamochi.banchus.packets.client.*;

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
        } else {
            throw new UnsupportedOperationException("Packet id " + packetId + " not supported yet.");
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
        // TODO: idk
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

    public PongPacket readPong(InputStream stream) throws IOException {
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

    public ReceiveUpdatesPacket readReceiveUpdates(InputStream stream) throws IOException {
        // TODO: idk
        return new ReceiveUpdatesPacket();
    }

    public UserStatsRequestPacket readUserStatsRequest(InputStream stream) throws IOException {
        // TODO: idk
        return new UserStatsRequestPacket();
    }
}
