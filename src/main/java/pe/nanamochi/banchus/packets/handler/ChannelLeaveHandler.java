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
import pe.nanamochi.banchus.packets.client.ChannelLeavePacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.ChannelAvailablePacket;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(value = Packets.OSU_CHANNEL_LEAVE, checkForRestriction = true)
public class ChannelLeaveHandler extends AbstractPacketHandler<ChannelLeavePacket> {
  private static final Logger logger = LoggerFactory.getLogger(ChannelLeaveHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;

  @Override
  public void handle(
      ChannelLeavePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    Channel channel = channelService.findByName(packet.getName());

    // #lobby has its own handler
    if (channel == null
        || (channel.getName().equals("#lobby") && session.isReceiveMatchUpdates())) {
      return;
    }

    Set<UUID> currentMembers = channelMembersService.getMembers(channel.getId());

    if (!currentMembers.contains(session.getId())) {
      logger.warn(
          "User {} attempted to leave a channel they are not in.", session.getUser().getUsername());
      return;
    }
    channelMembersService.removeMemberFromChannel(channel, session);

    // TODO: Only get all sessions that has any privilege bit
    for (Session otherOsuSession : sessionService.getAllSessions()) {
      if (!channelService.canReadChannel(channel, session.getUser().getPrivileges())) continue;
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      int newMemberCount = !currentMembers.isEmpty() ? currentMembers.size() - 1 : 0;
      packetWriter.writePacket(
          stream,
          new ChannelAvailablePacket(channel.getName(), channel.getTopic(), newMemberCount));
      packetBundleService.enqueue(otherOsuSession.getId(), new PacketBundle(stream.toByteArray()));
    }

    logger.info("User {} has left channel {}.", session.getUser().getUsername(), channel.getName());
  }
}
