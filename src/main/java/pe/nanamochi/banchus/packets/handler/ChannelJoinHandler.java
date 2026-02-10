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
import pe.nanamochi.banchus.packets.client.ChannelJoinPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.ChannelAvailablePacket;
import pe.nanamochi.banchus.packets.server.ChannelJoinSuccessPacket;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(value = Packets.OSU_CHANNEL_JOIN, checkForRestriction = true)
public class ChannelJoinHandler extends AbstractPacketHandler<ChannelJoinPacket> {
  private static final Logger logger = LoggerFactory.getLogger(ChannelJoinHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;

  @Override
  public void handle(
      ChannelJoinPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    Channel channel = channelService.findByName(packet.getName());

    if (channel == null
        || !channelService.canReadChannel(channel, session.getUser().getPrivileges())) return;

    Set<UUID> currentChannelMembers = channelMembersService.getMembers(channel.getId());

    if (currentChannelMembers.contains(session.getId())) {
      logger.warn(
          "User {} attempted to join a channel they are already in.",
          session.getUser().getUsername());
      return;
    }
    channelMembersService.addMemberToChannel(channel, session);

    ByteArrayOutputStream joinStream = new ByteArrayOutputStream();
    packetWriter.writePacket(joinStream, new ChannelJoinSuccessPacket(channel.getName()));
    packetBundleService.enqueue(session.getId(), new PacketBundle(joinStream.toByteArray()));

    // TODO: Only get all sessions that has any privilege bit
    for (Session otherOsuSession : sessionService.getAllSessions()) {
      if (!channelService.canReadChannel(channel, session.getUser().getPrivileges())) continue;
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(
          stream,
          new ChannelAvailablePacket(
              channel.getName(), channel.getTopic(), currentChannelMembers.size() + 1));
      packetBundleService.enqueue(otherOsuSession.getId(), new PacketBundle(stream.toByteArray()));
    }

    logger.info(
        "User {} has joined channel {}.", session.getUser().getUsername(), channel.getName());
  }
}
