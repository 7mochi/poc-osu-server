package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.client.StopSpectatingPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.gameplay.SpectatorService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(value = Packets.OSU_STOP_SPECTATING, checkForRestriction = true)
public class StopSpectatingHandler extends AbstractPacketHandler<StopSpectatingPacket> {
  private static final Logger logger = LoggerFactory.getLogger(StopSpectatingHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final SpectatorService spectatorService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;

  @Override
  public void handle(
      StopSpectatingPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getUser().isRestricted()) {
      return;
    }

    Session hostSession = sessionService.getSessionByID(session.getSpectatorHostSessionId());

    if (hostSession == null) {
      logger.warn(
          "The user ({}) attempted to stop spectating another user who is offline.",
          session.getUser().getId());
      return;
    }

    spectatorService.remove(hostSession.getId(), session.getId());

    session.setSpectatorHostSessionId(null);
    session = sessionService.updateSession(session);

    Channel spectatorChannel = channelService.findByName("#spec_" + hostSession.getId());

    channelMembersService.removeMemberFromChannel(spectatorChannel, session);

    Set<UUID> currentChannelMemberIds = channelMembersService.getMembers(spectatorChannel.getId());
    for (UUID memberSessionId : currentChannelMemberIds) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(
          stream,
          new ChannelAvailablePacket(
              "#spectator", spectatorChannel.getTopic(), currentChannelMemberIds.size()));
      packetBundleService.enqueue(memberSessionId, new PacketBundle(stream.toByteArray()));
    }

    // Only the host remains
    if (currentChannelMemberIds.size() == 1) {
      // Remove the host from the channel
      channelMembersService.removeMemberFromChannel(spectatorChannel, hostSession);

      // Delete the channel
      channelService.delete(spectatorChannel);

      // Inform the host that the channel was deleted
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new ChannelRevokedPacket("#spectator"));
      packetBundleService.enqueue(hostSession.getId(), new PacketBundle(stream.toByteArray()));

      logger.info("Spectator channel closed due to no spectators.");
    }

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(stream, new SpectatorLeftPacket(session.getUser().getId()));
    packetBundleService.enqueue(hostSession.getId(), new PacketBundle(stream.toByteArray()));

    for (UUID spectatorSessionId : spectatorService.getMembers(hostSession.getId())) {
      if (spectatorSessionId.equals(session.getId())) {
        continue;
      }

      stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new FellowSpectatorLeftPacket(session.getUser().getId()));
      packetBundleService.enqueue(spectatorSessionId, new PacketBundle(stream.toByteArray()));
    }
  }
}
