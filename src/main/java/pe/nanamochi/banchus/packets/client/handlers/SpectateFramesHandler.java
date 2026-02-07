package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.SpectateFramesPacket;
import pe.nanamochi.banchus.services.*;

@Component
@RequiredArgsConstructor
public class SpectateFramesHandler extends AbstractPacketHandler<SpectateFramesPacket> {
  private static final Logger logger = LoggerFactory.getLogger(SpectateFramesHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SpectatorService spectatorService;

  @Override
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_SPECTATE_FRAMES;
  }

  @Override
  public Class<SpectateFramesPacket> getPacketClass() {
    return SpectateFramesPacket.class;
  }

  @Override
  public void handle(
      SpectateFramesPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());

    if (session.getUser().isRestricted()) {
      return;
    }

    for (UUID spectateSessionId : spectatorService.getMembers(session.getId())) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(
          stream,
          new pe.nanamochi.banchus.packets.server.SpectateFramesPacket(
              packet.getReplayFrameBundle()));
      packetBundleService.enqueue(spectateSessionId, new PacketBundle(stream.toByteArray()));
    }
  }
}
