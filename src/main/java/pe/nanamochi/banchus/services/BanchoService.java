package pe.nanamochi.banchus.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.PacketBundle;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.PacketHandler;
import pe.nanamochi.banchus.packets.PacketReader;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.server.AnnouncePacket;
import pe.nanamochi.banchus.packets.server.RestartPacket;

@Service
@RequiredArgsConstructor
public class BanchoService {
  private final SessionService sessionService;
  private final PacketWriter packetWriter;
  private final PacketReader packetReader;
  private final PacketHandler packetHandler;
  private final PacketBundleService packetBundleService;

  public byte[] handleBanchoRequest(HttpHeaders headers, byte[] data, ByteArrayOutputStream stream)
      throws IOException {
    Session session =
        sessionService.getSessionByID(
            UUID.fromString(Objects.requireNonNull(headers.getFirst("osu-token"))));

    if (session == null) {
      packetWriter.writePacket(stream, new RestartPacket(0));
      packetWriter.writePacket(stream, new AnnouncePacket("The server has restarted."));
      return stream.toByteArray();
    }

    session.setLastCommunicatedAt(Instant.now());
    sessionService.updateSession(session);

    // Read packets from request body and handle them
    List<Packet> packets = packetReader.readPackets(data);
    for (Packet packet : packets) {
      packetHandler.handlePacket(packet, session, stream);
    }

    // Dequeue all packets to send back to the client
    List<PacketBundle> ownPacketBundles = packetBundleService.dequeueAll(session.getId());
    for (PacketBundle packetBundle : ownPacketBundles) {
      stream.write(packetBundle.getData());
    }

    headers.add("cho-token", session.getId().toString());

    return stream.toByteArray();
  }
}
