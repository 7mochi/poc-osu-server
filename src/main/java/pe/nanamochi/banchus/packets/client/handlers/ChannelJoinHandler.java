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
import pe.nanamochi.banchus.packets.client.ChannelJoinPacket;
import pe.nanamochi.banchus.packets.server.ChannelAvailablePacket;
import pe.nanamochi.banchus.packets.server.ChannelJoinSuccessPacket;
import pe.nanamochi.banchus.services.ChannelMembersService;
import pe.nanamochi.banchus.services.ChannelService;
import pe.nanamochi.banchus.services.PacketBundleService;
import pe.nanamochi.banchus.services.SessionService;

@Component
public class ChannelJoinHandler extends AbstractPacketHandler<ChannelJoinPacket> {
  private static final Logger logger = LoggerFactory.getLogger(ChannelJoinHandler.class);

  @Autowired private PacketWriter packetWriter;
  @Autowired private PacketBundleService packetBundleService;
  @Autowired private SessionService sessionService;
  @Autowired private ChannelService channelService;
  @Autowired private ChannelMembersService channelMembersService;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_CHANNEL_JOIN;
  }

  @Override
  public Class<ChannelJoinPacket> getPacketClass() {
    return ChannelJoinPacket.class;
  }

  @Override
  public void handle(
      ChannelJoinPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
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
