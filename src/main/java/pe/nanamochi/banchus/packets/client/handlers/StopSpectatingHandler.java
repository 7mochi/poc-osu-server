package pe.nanamochi.banchus.packets.client.handlers;

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
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.StopSpectatingPacket;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.*;

@Component
@RequiredArgsConstructor
public class StopSpectatingHandler extends AbstractPacketHandler<StopSpectatingPacket> {
  private static final Logger logger = LoggerFactory.getLogger(StopSpectatingHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final SpectatorService spectatorService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;

  @Override
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_STOP_SPECTATING;
  }

  @Override
  public Class<StopSpectatingPacket> getPacketClass() {
    return StopSpectatingPacket.class;
  }

  @Override
  public void handle(
      StopSpectatingPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());

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
