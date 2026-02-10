package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.MatchStatus;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.packets.client.MatchCompletePacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_COMPLETE)
public class MatchCompleteHandler extends AbstractPacketHandler<MatchCompletePacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchCompleteHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;
  private final PacketWriter packetWriter;

  @Override
  public void handle(
      MatchCompletePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} attempted to tell us they completed but they are not in a match.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerSlot slot =
        multiplayerService.findSlotBySessionId(session.getMultiplayerMatchId(), session.getId());
    if (slot == null) {
      logger.warn(
          "User {} attempted to tell us they completed but they don't have a slot.",
          session.getUser().getUsername());
      return;
    }

    slot.setStatus(SlotStatus.WAITING_FOR_END.getValue());
    multiplayerService.updateSlot(session.getMultiplayerMatchId(), slot);

    boolean allDone = multiplayerService.allCompleted(session.getMultiplayerMatchId());
    if (!allDone) return;

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());
    match.setStatus(MatchStatus.WAITING);
    multiplayerService.update(match);

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(stream, new pe.nanamochi.banchus.packets.server.MatchCompletePacket());
    matchBroadcastService.broadcastToMatch(
        session.getMultiplayerMatchId(), stream.toByteArray(), SlotStatus.COMPLETE.getValue());

    List<MultiplayerSlot> slots = multiplayerService.getAllSlots(session.getMultiplayerMatchId());
    for (MultiplayerSlot s : slots) {
      if (s.getStatus() == SlotStatus.WAITING_FOR_END.getValue()) {
        s.setStatus(SlotStatus.NOT_READY.getValue());
        s.setLoaded(false);
        s.setSkipped(false);
        multiplayerService.updateSlot(session.getMultiplayerMatchId(), s);
      }
    }

    matchBroadcastService.broadcastMatchUpdates(session.getMultiplayerMatchId(), true, List.of());

    logger.info("All players in match {} have completed the map.", session.getMultiplayerMatchId());
  }
}
