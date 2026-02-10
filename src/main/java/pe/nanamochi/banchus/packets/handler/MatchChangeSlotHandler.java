package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.commons.SlotTeam;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.packets.client.MatchChangeSlotPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_CHANGE_SLOT)
public class MatchChangeSlotHandler extends AbstractPacketHandler<MatchChangeSlotPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchChangeSlotHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  public void handle(
      MatchChangeSlotPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} tried to change slot while not in a match.", session.getUser().getUsername());
      return;
    }

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());

    MultiplayerSlot currentSlot =
        multiplayerService.findSlotBySessionId(match.getMatchId(), session.getId());
    if (currentSlot == null) {
      logger.warn("User {} not inside of a slot.", session.getUser().getUsername());
      return;
    }

    MultiplayerSlot targetSlot =
        multiplayerService.findSlotById(match.getMatchId(), packet.getSlotId());
    if (targetSlot == null) {
      logger.warn(
          "User {} tried to change to a slot that doesn't exist: {}.",
          session.getUser().getUsername(),
          packet.getSlotId());
      return;
    }

    if (SlotStatus.fromValue(targetSlot.getStatus()) != SlotStatus.OPEN) {
      logger.warn(
          "User {} tried to change to a slot that is not open: {}.",
          session.getUser().getUsername(),
          packet.getSlotId());
      return;
    }

    // Switch to new slot
    targetSlot.setUserId(currentSlot.getUserId());
    targetSlot.setSessionId(currentSlot.getSessionId());
    targetSlot.setStatus(currentSlot.getStatus());
    targetSlot.setTeam(currentSlot.getTeam());
    targetSlot.setMods(currentSlot.getMods());
    targetSlot.setLoaded(currentSlot.isLoaded());
    targetSlot.setSkipped(currentSlot.isSkipped());
    targetSlot = multiplayerService.updateSlot(match.getMatchId(), targetSlot);

    // Open up old slot
    currentSlot.setUserId(-1);
    currentSlot.setSessionId(null);
    currentSlot.setStatus(SlotStatus.OPEN.getValue());
    currentSlot.setTeam(SlotTeam.NEUTRAL);
    currentSlot.setMods(0);
    currentSlot.setLoaded(false);
    currentSlot.setSkipped(false);
    multiplayerService.updateSlot(match.getMatchId(), currentSlot);

    logger.info(
        "User {} switched from slot {} to slot {} in match {}.",
        session.getUser().getUsername(),
        currentSlot.getSlotId(),
        targetSlot.getSlotId(),
        match.getMatchId());

    // Send updated data to those in the multi match, and #lobby
    matchBroadcastService.broadcastMatchUpdates(match.getMatchId(), true, List.of());
  }
}
