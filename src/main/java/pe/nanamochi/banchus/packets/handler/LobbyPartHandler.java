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
import pe.nanamochi.banchus.packets.client.LobbyPartPacket;
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
@HandleClientPacket(Packets.OSU_LOBBY_PART)
public class LobbyPartHandler extends AbstractPacketHandler<LobbyPartPacket> {
  private static final Logger logger = LoggerFactory.getLogger(LobbyPartHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;

  @Override
  public void handle(LobbyPartPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
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
