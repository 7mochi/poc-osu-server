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
import pe.nanamochi.banchus.packets.server.UserQuitPacket;
import pe.nanamochi.banchus.services.ChannelMembersService;
import pe.nanamochi.banchus.services.ChannelService;
import pe.nanamochi.banchus.services.PacketBundleService;
import pe.nanamochi.banchus.services.SessionService;

@Component
public class ExitPacketHandler extends AbstractPacketHandler<ExitPacket> {

  private static final Logger logger = LoggerFactory.getLogger(ExitPacketHandler.class);

  @Autowired private PacketWriter packetWriter;
  @Autowired private PacketBundleService packetBundleService;
  @Autowired private SessionService sessionService;
  @Autowired private ChannelService channelService;
  @Autowired private ChannelMembersService channelMembersService;

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

    // Leave channels the osu session is in
    for (Channel channel : channelService.getAllChannels()) {
      if (channelMembersService.removeMemberFromChannel(channel, session) != null) {
        // Inform everyone in the channel that we left
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

    // TODO: Spectator

    // TODO: Multiplayer

    // Tell everyone else we logout
    // TODO: Only do this if our privileges allow it (unrestricted)
    for (Session otherOsuSession : sessionService.getAllSessions()) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(
          stream, new UserQuitPacket(session.getUser().getId(), QuitState.GONE));
      packetBundleService.enqueue(otherOsuSession.getId(), new PacketBundle(stream.toByteArray()));
    }

    logger.info("User {} has logged out.", session.getUser().getUsername());
  }
}
