package pe.nanamochi.banchus.packets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.client.*;

import java.io.ByteArrayOutputStream;

@Component
public class PacketHandler {

    @Autowired
    private PacketWriter packetWriter;

    public void handlePacket(Packet packet, Session session, ByteArrayOutputStream responseStream) {
        System.out.println("Handling packet: " + packet.getPacketType());

        switch (packet.getPacketType()) {
            case OSU_USER_STATUS -> handleUserStatus((UserStatusPacket) packet, session, responseStream);
            case OSU_MESSAGE -> handleMessage((MessagePacket) packet, session, responseStream);
            case OSU_EXIT -> handleExit((ExitPacket) packet, session, responseStream);
            case OSU_STATUS_UPDATE_REQUEST -> handleStatusUpdateRequest((StatusUpdateRequestPacket) packet, session, responseStream);
            case OSU_PONG -> handlePong((PongPacket) packet, session, responseStream);
            case OSU_PRIVATE_MESSAGE -> handlePrivateMessage((PrivateMessagePacket) packet, session, responseStream);
            case OSU_RECEIVE_UPDATES -> handleReceiveUpdates((ReceiveUpdatesPacket) packet, session, responseStream);
            case OSU_USER_STATS_REQUEST -> handleUserStatsRequest((UserStatsRequestPacket) packet, session, responseStream);
            default -> System.out.println("Unhandled packet type: " + packet.getPacketType());
        }
    }

    private void handleUserStatus(UserStatusPacket packet, Session session, ByteArrayOutputStream responseStream) {

    }

    private void handleMessage(MessagePacket packet, Session session, ByteArrayOutputStream responseStream) {

    }

    private void handleExit(ExitPacket packet, Session session, ByteArrayOutputStream responseStream) {

    }

    private void handleStatusUpdateRequest(StatusUpdateRequestPacket packet, Session session, ByteArrayOutputStream responseStream) {

    }

    private void handlePong(PongPacket packet, Session session, ByteArrayOutputStream responseStream) {

    }

    private void handlePrivateMessage(PrivateMessagePacket packet, Session session, ByteArrayOutputStream responseStream) {

    }

    private void handleReceiveUpdates(ReceiveUpdatesPacket packet, Session session, ByteArrayOutputStream responseStream) {

    }

    private void handleUserStatsRequest(UserStatsRequestPacket packet, Session session, ByteArrayOutputStream responseStream) {

    }
}


