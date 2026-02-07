package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.Mods;
import pe.nanamochi.banchus.entities.QuitState;
import pe.nanamochi.banchus.entities.SlotStatus;
import pe.nanamochi.banchus.entities.SlotTeam;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.ExitPacket;
import pe.nanamochi.banchus.packets.server.ChannelAvailablePacket;
import pe.nanamochi.banchus.packets.server.ChannelRevokedPacket;
import pe.nanamochi.banchus.packets.server.MatchDisbandPacket;
import pe.nanamochi.banchus.packets.server.UserQuitPacket;
import pe.nanamochi.banchus.services.*;

@Component
@RequiredArgsConstructor
public class ExitPacketHandler extends AbstractPacketHandler<ExitPacket> {

  private static final Logger logger = LoggerFactory.getLogger(ExitPacketHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;
  private final SpectatorService spectatorService;
  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_EXIT;
  }

  @Override
  public Class<ExitPacket> getPacketClass() {
    return ExitPacket.class;
  }

  @Override
  public void handle(ExitPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
    // The osu! client will often attempt to logout as soon as they login,
    // this is a quirk of the client, and we don't really want to log them out;
    // so we ignore this case if it's been < 1 second since the client's login
    if (Duration.between(session.getCreatedAt(), Instant.now()).compareTo(Duration.ofSeconds(1))
        < 0) {
      return;
    }

    session = sessionService.deleteSession(session);

    // Handle the user spectating another user
    if (session.getSpectatorHostSessionId() != null) {
      spectatorService.remove(session.getSpectatorHostSessionId(), session.getId());
    } else {
      // Handle some users spectating us
      Set<UUID> ourSpectatorsId = spectatorService.getMembers(session.getId());

      if (!ourSpectatorsId.isEmpty()) {
        // We have spectators
        Channel spectatorChannel = channelService.findByName("#spec_" + session.getId());

        for (UUID spectatorSessionId : ourSpectatorsId) {
          // Remove them from our spectators
          spectatorService.remove(session.getId(), spectatorSessionId);

          // Remove them from the #spectator channel
          channelMembersService.removeMemberFromChannel(
              spectatorChannel, sessionService.getSessionByID(spectatorSessionId));
          ByteArrayOutputStream stream = new ByteArrayOutputStream();
          packetWriter.writePacket(stream, new ChannelRevokedPacket("#spectator"));
          packetBundleService.enqueue(spectatorSessionId, new PacketBundle(stream.toByteArray()));
        }

        // Remove us from the #spectator channel
        channelMembersService.removeMemberFromChannel(spectatorChannel, session);

        // Delete the #spectator channel
        channelService.delete(spectatorChannel);
      }
    }

    // Handle the player being in a multiplayer match
    if (session.getMultiplayerMatchId() != null && session.getMultiplayerMatchId() != -1) {
      MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());

      // Fetch our slot
      MultiplayerSlot slot =
          multiplayerService.findSlotBySessionId(match.getMatchId(), session.getId());

      // Remove us from the match
      slot.setUserId(-1);
      slot.setSessionId(null);
      slot.setStatus(SlotStatus.OPEN.getValue());
      slot.setTeam(SlotTeam.NEUTRAL);
      slot.setMods(Mods.NO_MOD.getValue());
      slot.setLoaded(false);
      slot.setSkipped(false);
      multiplayerService.updateSlot(match.getMatchId(), slot);

      Channel matchChannel = channelService.findByName("#mp_" + match.getMatchId());
      if (match.getHostUserId() == session.getUser().getId()) {
        // If the host left, pick a new host
        List<MultiplayerSlot> slots = multiplayerService.getAllSlots(match.getMatchId());

        MultiplayerSlot newHostSlot = null;
        for (MultiplayerSlot s : slots) {
          // Slot doesn't have a user
          if (slot.getUserId() == -1) continue;
          newHostSlot = s;
          break;
        }

        // No one is left in the match, close it
        if (newHostSlot == null) {
          ByteArrayOutputStream stream = new ByteArrayOutputStream();
          packetWriter.writePacket(stream, new MatchDisbandPacket(match.getMatchId()));
          matchBroadcastService.broadcastToLobby(stream.toByteArray());

          channelMembersService.removeMemberFromChannel(matchChannel, session);
          stream = new ByteArrayOutputStream();
          packetWriter.writePacket(stream, new MatchDisbandPacket(match.getMatchId()));
          packetWriter.writePacket(stream, new ChannelRevokedPacket("#multiplayer"));
          packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));

          // Delete the multiplayer match (and it's slots)
          match = multiplayerService.delete(match.getMatchId());

          // Delete the #multiplayer channel
          matchChannel = channelService.delete(matchChannel);

          logger.info(
              "Multiplayer match {} closed due to no remaining players.", match.getMatchId());
        }
      }
    }

    // Handle the player being in any chat channels
    for (Channel channel : channelService.getAllChannels()) {
      if (channelMembersService.removeMemberFromChannel(channel, session) != null) {
        // Update the channel info for everyone else
        Set<UUID> currentChannelMembers = channelMembersService.getMembers(channel.getId());

        for (UUID sessionId : currentChannelMembers) {
          ByteArrayOutputStream stream = new ByteArrayOutputStream();
          packetWriter.writePacket(
              stream,
              new ChannelAvailablePacket(
                  channel.getName(), channel.getTopic(), currentChannelMembers.size()));
          packetBundleService.enqueue(sessionId, new PacketBundle(stream.toByteArray()));
        }
      }
    }

    // Tell everyone else we logout
    if (!session.getUser().isRestricted()) {
      for (Session otherOsuSession : sessionService.getAllSessions()) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        packetWriter.writePacket(
            stream, new UserQuitPacket(session.getUser().getId(), QuitState.GONE));
        packetBundleService.enqueue(
            otherOsuSession.getId(), new PacketBundle(stream.toByteArray()));
      }
    }

    logger.info("User {} has logged out.", session.getUser().getUsername());
  }
}
