package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.packets.client.MatchNoBeatmapPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_NO_BEATMAP)
public class MatchNoBeatmapHandler extends AbstractPacketHandler<MatchNoBeatmapPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchNoBeatmapHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  public void handle(
      MatchNoBeatmapPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} attempted to tell us they have no beatmap but they are not in a match.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerSlot slot =
        multiplayerService.findSlotBySessionId(session.getMultiplayerMatchId(), session.getId());
    if (slot == null) {
      logger.warn(
          "User {} attempted to tell us they have no beatmap but they don't have a slot.",
          session.getUser().getUsername());
      return;
    }

    if (slot.getStatus() != SlotStatus.NOT_READY.getValue()) {
      logger.warn(
          "User {} attempted to tell us they have no beatmap but they are not allowed to.",
          session.getUser().getUsername());
      return;
    }

    slot.setStatus(SlotStatus.NO_BEATMAP.getValue());
    multiplayerService.updateSlot(session.getMultiplayerMatchId(), slot);

    matchBroadcastService.broadcastMatchUpdates(session.getMultiplayerMatchId(), true, List.of());
  }
}
