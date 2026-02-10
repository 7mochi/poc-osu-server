package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.commons.SlotTeam;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.client.MatchLockPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_LOCK)
public class MatchLockHandler extends AbstractPacketHandler<MatchLockPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchLockHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;
  private final SessionService sessionService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;
  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;

  @Override
  public void handle(MatchLockPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} tried to (un)lock a slot but they are not in a match.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());
    if (match == null) {
      logger.warn(
          "User {} tried to (un)lock a slot but their match doesn't exist.",
          session.getUser().getUsername());
      return;
    }

    // Only the host can edit slots
    if (match.getHostUserId() != session.getUser().getId()) {
      logger.warn(
          "User {} tried to (un)lock a slot but they are not the host.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerSlot slot = multiplayerService.findSlotById(match.getMatchId(), packet.getSlotId());
    if (slot == null) {
      logger.warn(
          "User {} tried to (un)lock a slot that doesn't exist: {}.",
          session.getUser().getUsername(),
          packet.getSlotId());
      return;
    }

    Session slotSession = null;
    if (slot.getUserId() != -1) {
      if (slot.getUserId() == session.getUser().getId()) {
        logger.warn(
            "User {} tried to (un)lock a slot that they are currently occupying: {}.",
            session.getUser().getUsername(),
            packet.getSlotId());
        return;
      }

      slotSession = sessionService.getPrimarySessionByUserId(slot.getUserId());

      Channel matchChannel = channelService.findByName("#mp_" + match.getMatchId());
      channelMembersService.removeMemberFromChannel(matchChannel, slotSession);

      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new ChannelRevokedPacket("#multiplayer"));
      packetBundleService.enqueue(slotSession.getId(), new PacketBundle(stream.toByteArray()));

      logger.info(
          "User {} was kicked from match {} by host {}.",
          slotSession.getUser().getUsername(),
          match.getMatchId(),
          session.getUser().getUsername());
    }

    SlotStatus newStatus = SlotStatus.LOCKED;
    if (SlotStatus.fromValue(slot.getStatus()) == SlotStatus.LOCKED) {
      newStatus = SlotStatus.OPEN;
    }

    // Lock slot
    slot.setUserId(-1);
    slot.setSessionId(null);
    slot.setStatus(newStatus.getValue());
    slot.setTeam(SlotTeam.NEUTRAL);
    slot.setMods(0);
    slot.setLoaded(false);
    slot.setSkipped(false);
    multiplayerService.updateSlot(match.getMatchId(), slot);

    // Inform relevant places of the new match state
    List<UUID> extraOsuSessionIds = slotSession != null ? List.of(slotSession.getId()) : List.of();
    matchBroadcastService.broadcastMatchUpdates(match.getMatchId(), true, extraOsuSessionIds);

    logger.info(
        "User {} {} slot {} in match {}.",
        session.getUser().getUsername(),
        newStatus == SlotStatus.LOCKED ? "locked" : "unlocked",
        packet.getSlotId(),
        match.getMatchId());
  }
}
