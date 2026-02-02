package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.PacketBundle;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.ChannelLeavePacket;
import pe.nanamochi.banchus.packets.server.ChannelAvailablePacket;
import pe.nanamochi.banchus.services.ChannelMembersService;
import pe.nanamochi.banchus.services.ChannelService;
import pe.nanamochi.banchus.services.PacketBundleService;
import pe.nanamochi.banchus.services.SessionService;

@Component
public class ChannelLeaveHandler extends AbstractPacketHandler<ChannelLeavePacket> {
  private static final Logger logger = LoggerFactory.getLogger(ChannelLeaveHandler.class);

  @Autowired private PacketWriter packetWriter;
  @Autowired private PacketBundleService packetBundleService;
  @Autowired private SessionService sessionService;
  @Autowired private ChannelService channelService;
  @Autowired private ChannelMembersService channelMembersService;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_CHANNEL_LEAVE;
  }

  @Override
  public Class<ChannelLeavePacket> getPacketClass() {
    return ChannelLeavePacket.class;
  }

  @Override
  public void handle(
      ChannelLeavePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
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
