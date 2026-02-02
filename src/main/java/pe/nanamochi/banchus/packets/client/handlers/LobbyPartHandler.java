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
import pe.nanamochi.banchus.packets.client.LobbyPartPacket;
import pe.nanamochi.banchus.packets.server.ChannelAvailablePacket;
import pe.nanamochi.banchus.services.ChannelMembersService;
import pe.nanamochi.banchus.services.ChannelService;
import pe.nanamochi.banchus.services.PacketBundleService;
import pe.nanamochi.banchus.services.SessionService;

@Component
public class LobbyPartHandler extends AbstractPacketHandler<LobbyPartPacket> {
  private static final Logger logger = LoggerFactory.getLogger(LobbyPartHandler.class);

  @Autowired private PacketWriter packetWriter;
  @Autowired private PacketBundleService packetBundleService;
  @Autowired private SessionService sessionService;
  @Autowired private ChannelService channelService;
  @Autowired private ChannelMembersService channelMembersService;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_LOBBY_PART;
  }

  @Override
  public Class<LobbyPartPacket> getPacketClass() {
    return LobbyPartPacket.class;
  }

  @Override
  public void handle(LobbyPartPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
    session.setReceiveMatchUpdates(false);
    sessionService.updateSession(session);

    Channel channel = channelService.findByName("#lobby");

    if (channel == null) return;

    channelMembersService.removeMemberFromChannel(channel, session);

    Set<UUID> currentChannelMembers = channelMembersService.getMembers(channel.getId());

    // TODO: Only get all sessions that has any privilege bit
    for (Session otherOsuSession : sessionService.getAllSessions()) {
      if (!channelService.canReadChannel(channel, session.getUser().getPrivileges())) continue;
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(
          stream,
          new ChannelAvailablePacket(
              channel.getName(), channel.getTopic(), currentChannelMembers.size()));
      packetBundleService.enqueue(otherOsuSession.getId(), new PacketBundle(stream.toByteArray()));
    }

    logger.info("User {} has part from #lobby.", session.getUser().getUsername());
  }
}
