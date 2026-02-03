package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.PacketBundle;
import pe.nanamochi.banchus.entities.QuitState;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.ExitPacket;
import pe.nanamochi.banchus.packets.server.ChannelAvailablePacket;
import pe.nanamochi.banchus.packets.server.ChannelRevokedPacket;
import pe.nanamochi.banchus.packets.server.UserQuitPacket;
import pe.nanamochi.banchus.services.*;

@Component
public class ExitPacketHandler extends AbstractPacketHandler<ExitPacket> {

  private static final Logger logger = LoggerFactory.getLogger(ExitPacketHandler.class);

  @Autowired private PacketWriter packetWriter;
  @Autowired private PacketBundleService packetBundleService;
  @Autowired private SessionService sessionService;
  @Autowired private ChannelService channelService;
  @Autowired private ChannelMembersService channelMembersService;
  @Autowired private SpectatorService spectatorService;

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

    // TODO: Multiplayer

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
