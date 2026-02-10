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
import pe.nanamochi.banchus.packets.client.MatchPartPacket;
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
@HandleClientPacket(Packets.OSU_MATCH_PART)
public class MatchPartHandler extends AbstractPacketHandler<MatchPartPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchPartHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final MultiplayerService multiplayerService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  public void handle(MatchPartPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} tried to leave a match while not in a match.", session.getUser().getUsername());
      return;
    }

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());
    if (match == null) {
      logger.warn(
          "User {} tried to leave a match that doesn't exist.", session.getUser().getUsername());
      session.setMultiplayerMatchId(-1);
      sessionService.updateSession(session);
      return;
    }

    MultiplayerSlot currentSlot =
        multiplayerService.findSlotBySessionId(match.getMatchId(), session.getId());
    if (currentSlot == null) {
      // NOTE: this typically happens when a session is kicked from a match
      logger.warn(
          "User {} attempted to leave their match but they don't have a slot.",
          session.getUser().getUsername());
      return;
    }

    // Open up old slot
    currentSlot.setUserId(-1);
    currentSlot.setSessionId(null);
    currentSlot.setStatus(SlotStatus.OPEN.getValue());
    currentSlot.setTeam(SlotTeam.NEUTRAL);
    currentSlot.setMods(0);
    currentSlot.setLoaded(false);
    currentSlot.setSkipped(false);
    currentSlot = multiplayerService.updateSlot(match.getMatchId(), currentSlot);

    Channel matchChannel = channelService.findByName("#mp_" + match.getMatchId());

    if (match.getHostUserId() == session.getUser().getId()) {
      // If the host left, pick a new host
      List<MultiplayerSlot> slots = multiplayerService.getAllSlots(match.getMatchId());
      MultiplayerSlot newHostSlot = null;
      for (MultiplayerSlot slot : slots) {
        // Slot doesn't have a user
        if (slot.getUserId() == -1) continue;
        newHostSlot = slot;
        break;
      }

      // No one is left in the match, close it
      if (newHostSlot == null) {
        Channel lobbyChannel = channelService.findByName("#lobby");

        // Inform everyone in the lobby that the match no longer exists
        for (UUID otherSessionId : channelMembersService.getMembers(lobbyChannel.getId())) {
          ByteArrayOutputStream stream = new ByteArrayOutputStream();
          packetWriter.writePacket(stream, new MatchDisbandPacket(match.getMatchId()));
          packetBundleService.enqueue(otherSessionId, new PacketBundle(stream.toByteArray()));
        }

        // Kick everyone out of the multiplayer match and channel
        for (UUID otherSessionId : channelMembersService.getMembers(lobbyChannel.getId())) {
          ByteArrayOutputStream stream = new ByteArrayOutputStream();
          packetWriter.writePacket(stream, new MatchDisbandPacket(match.getMatchId()));
          packetWriter.writePacket(stream, new ChannelRevokedPacket("#multiplayer"));
          packetBundleService.enqueue(otherSessionId, new PacketBundle(stream.toByteArray()));
          channelMembersService.removeMemberFromChannel(
              matchChannel, sessionService.getSessionByID(otherSessionId));
        }

        // Delete the multiplayer channel and it's slots
        match = multiplayerService.delete(match.getMatchId());
        for (MultiplayerSlot slot : slots) {
          multiplayerService.deleteSlot(match.getMatchId(), slot.getSlotId());
        }

        // Delete the multiplayer channel
        channelService.delete(matchChannel);

        // Clear match from session
        session.setMultiplayerMatchId(-1);
        sessionService.updateSession(session);

        logger.info(
            "Match {} disbanded as the host {} has left and no players remain.",
            match.getMatchId(),
            session.getUser().getUsername());
        return;
      }

      match.setHostUserId(newHostSlot.getUserId());
      match = multiplayerService.update(match);

      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new MatchTransferHostPacket());
      packetBundleService.enqueue(
          newHostSlot.getSessionId(), new PacketBundle(stream.toByteArray()));

      logger.info(
          "Match {} host {} has left. New host is user ID {}.",
          match.getMatchId(),
          session.getUser().getUsername(),
          newHostSlot.getUserId());
    }

    // Leave the multiplayer channel
    channelMembersService.removeMemberFromChannel(matchChannel, session);
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(stream, new ChannelRevokedPacket("#multiplayer"));
    packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));

    // Inform relevant places of the new match state
    matchBroadcastService.broadcastMatchUpdates(match.getMatchId(), true, List.of());

    logger.info("User {} has left match {}.", session.getUser().getUsername(), match.getMatchId());
  }
}
