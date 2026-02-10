package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.Mods;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.packets.client.MatchChangeModsPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_CHANGE_MODS)
public class MatchChangeModsHandler extends AbstractPacketHandler<MatchChangeModsPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchChangeModsHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;
  private final SessionService sessionService;

  @Override
  public void handle(
      MatchChangeModsPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} tried to change match mods while not in a match.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());

    if (match == null) {
      logger.warn(
          "User {} tried to change mods but their match doesn't exist.",
          session.getUser().getUsername());
      return;
    }

    // TODO: convert mode for client and server?

    boolean isHost = match.getHostUserId() == session.getUser().getId();

    if (match.isFreemodsEnabled()) {
      // Apply the speed changing mods to the match
      if (isHost) {
        match.setMods(packet.getMods() & Mods.SPEED_CHANGING);
        multiplayerService.update(match);
      }

      // And apply the non-speed changing mods to the slot
      MultiplayerSlot slot =
          multiplayerService.findSlotBySessionId(session.getMultiplayerMatchId(), session.getId());
      if (slot == null) {
        logger.warn(
            "User {} tried to change mods but their slot doesn't exist.",
            session.getUser().getUsername());
        return;
      }

      slot.setMods(packet.getMods() & ~Mods.SPEED_CHANGING);
      multiplayerService.updateSlot(session.getMultiplayerMatchId(), slot);

      // Set the sessions mode if needed
      if (session.getGamemode() != match.getMode()) {
        session.setGamemode(match.getMode());
        session = sessionService.updateSession(session);
      }
    } else if (isHost) {
      // Set all sessions mode if needed
      if (session.getGamemode() != match.getMode()) {
        List<MultiplayerSlot> slots =
            multiplayerService.getAllSlots(session.getMultiplayerMatchId());
        for (MultiplayerSlot slot : slots) {
          if (slot.getUserId() != 1) {
            Session slotSession = sessionService.getSessionByID(slot.getSessionId());
            slotSession.setGamemode(match.getMode());
            slotSession.setMods(packet.getMods());
          }
        }
      }

      match.setMode(match.getMode());
      match.setMods(packet.getMods());
      multiplayerService.update(match);
    } else {
      logger.warn(
          "User {} attempted to change the match mods but they aren't allowed to.",
          session.getUser().getUsername());
      return;
    }

    matchBroadcastService.broadcastMatchUpdates(session.getMultiplayerMatchId(), true, List.of());

    logger.info(
        "User {} changed the match mods to {}.",
        session.getUser().getUsername(),
        Mods.fromBitmask(packet.getMods()));
  }
}
