package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.packets.client.MatchLoadCompletePacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_LOAD_COMPLETE)
public class MatchLoadCompleteHandler extends AbstractPacketHandler<MatchLoadCompletePacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchLoadCompleteHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;
  private final PacketWriter packetWriter;

  @Override
  public void handle(
      MatchLoadCompletePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} attempted to tell us they have loaded but they are not in a match.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerSlot slot =
        multiplayerService.findSlotBySessionId(session.getMultiplayerMatchId(), session.getId());
    if (slot == null) {
      logger.warn(
          "User {} attempted to tell us they have loaded but they don't have a slot.",
          session.getUser().getUsername());
      return;
    }

    slot.setLoaded(true);
    multiplayerService.updateSlot(session.getMultiplayerMatchId(), slot);

    boolean allLoaded = multiplayerService.allLoaded(session.getMultiplayerMatchId());
    if (!allLoaded) return;

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(stream, new MatchAllPlayersLoadedPacket());
    matchBroadcastService.broadcastToMatch(
        session.getMultiplayerMatchId(), stream.toByteArray(), SlotStatus.PLAYING.getValue());
  }
}
