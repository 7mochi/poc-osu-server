package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.commands.CommandProcessor;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.client.MessagePacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(value = Packets.OSU_MESSAGE, checkForRestriction = true)
public class MessageHandler extends AbstractPacketHandler<MessagePacket> {
  private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

  @Value("${banchus.command-prefix}")
  private String commandPrefix;

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;
  private final CommandProcessor commandProcessor;

  @Override
  public void handle(MessagePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getUser().isSilenced()) {
      logger.warn(
          "User {} attempted to send a message while silenced.", session.getUser().getUsername());
      return;
    }

    String channelName = resolveChannelName(packet, session);
    if (channelName == null) return;

    Channel channel = channelService.findByName(channelName);
    if (channel == null) {
      logger.warn(
          "User {} attempted to send a message to non-existent channel {}.",
          session.getUser().getUsername(),
          channelName);
      return;
    }

    if (!channelService.canWriteChannel(channel, session.getUser().getPrivileges())) {
      logger.warn(
          "User {} attempted to send a message to channel {} without sufficient privileges.",
          session.getUser().getUsername(),
          channelName);
      return;
    }

    if (packet.getContent().length() > 2000) {
      packet.setContent(packet.getContent().substring(0, 2000) + "...");
    }

    boolean isPrivateCommand = packet.getContent().startsWith(commandPrefix + "help");

    if (!isPrivateCommand) {
      broadcastToChannel(packet, session, channel);
    }

    handleCommands(session, packet, channel, isPrivateCommand);
  }

  private String resolveChannelName(MessagePacket packet, Session session) {
    String target = packet.getTarget();

    if (target.equals("#multiplayer")) {
      return (session.getMultiplayerMatchId() != null)
          ? "#mp_" + session.getMultiplayerMatchId()
          : null;
    }

    if (target.equals("#spectator")) {
      UUID hostId =
          (session.getSpectatorHostSessionId() != null)
              ? session.getSpectatorHostSessionId()
              : session.getId();
      return "#spec_" + hostId;
    }

    return target;
  }

  private void broadcastToChannel(MessagePacket packet, Session session, Channel channel)
      throws IOException {
    byte[] msgBytes =
        createMessagePacket(
            session.getUser().getUsername(),
            packet.getContent(),
            packet.getTarget(),
            session.getUser().getId());

    Set<UUID> members = channelMembersService.getMembers(channel.getId());
    for (UUID targetId : members) {
      if (targetId.equals(session.getId())) continue;
      packetBundleService.enqueue(targetId, new PacketBundle(msgBytes));
    }
  }

  private byte[] createMessagePacket(String sender, String content, String target, int userId)
      throws IOException {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(
        stream,
        new pe.nanamochi.banchus.packets.server.MessagePacket(sender, content, target, userId));
    return stream.toByteArray();
  }

  private void handleCommands(
      Session session, MessagePacket packet, Channel channel, boolean isPrivateCommand)
      throws IOException {
    String result =
        commandProcessor.handle(commandPrefix, packet.getContent(), session.getUser(), channel);

    if (result == null || result.trim().isEmpty()) return;

    Set<UUID> targetSessions =
        isPrivateCommand
            ? Set.of(session.getId())
            : channelMembersService.getMembers(channel.getId());

    byte[] responsePacket = createMessagePacket("BanchoBot", result, packet.getTarget(), 1);
    for (UUID targetSessionId : targetSessions) {
      packetBundleService.enqueue(targetSessionId, new PacketBundle(responsePacket));
    }
  }
}
