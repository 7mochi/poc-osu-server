package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
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
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.MessagePacket;
import pe.nanamochi.banchus.services.ChannelMembersService;
import pe.nanamochi.banchus.services.ChannelService;
import pe.nanamochi.banchus.services.PacketBundleService;

@Component
@RequiredArgsConstructor
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
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_MESSAGE;
  }

  @Override
  public Class<MessagePacket> getPacketClass() {
    return MessagePacket.class;
  }

  @Override
  public void handle(MessagePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());
    // TODO: Validate silence

    String channelName = null;
    if (packet.getTarget().equals("#multiplayer")) {
      Integer multiplayerMatchId = session.getMultiplayerMatchId();
      if (multiplayerMatchId == null) {
        logger.warn(
            "User {} tried to send a message in #multiplayer without being in a match.",
            session.getUser().getUsername());
        return;
      }

      channelName = "#mp_" + multiplayerMatchId;
    } else if (packet.getTarget().equals("#spectator")) {
      // We may be spectating someone, or may be the host of spectators
      UUID spectatorHostSessionId;
      if (session.getSpectatorHostSessionId() != null) {
        spectatorHostSessionId = session.getSpectatorHostSessionId();
      } else {
        spectatorHostSessionId = session.getId();
      }

      channelName = "#spec_" + spectatorHostSessionId;
    } else {
      channelName = packet.getTarget();
    }

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

    // Send message to everyone else
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(
        stream,
        new pe.nanamochi.banchus.packets.server.MessagePacket(
            session.getUser().getUsername(),
            packet.getContent(),
            packet.getTarget(),
            session.getUser().getId()));

    Set<UUID> targetSessions = new HashSet<>();

    if (!packet.getContent().startsWith("!help")) {
      targetSessions = channelMembersService.getMembers(channel.getId());
    }

    for (UUID targetSessionId : targetSessions) {
      if (targetSessionId.equals(session.getId())) continue; // Already sent to self
      packetBundleService.enqueue(targetSessionId, new PacketBundle(stream.toByteArray()));
    }

    handleCommands(session, packet, targetSessions, channel);
  }

  private void handleCommands(
      Session session, MessagePacket packet, Set<UUID> targetSessions, Channel channel)
      throws IOException {
    String result =
        commandProcessor.handle(commandPrefix, packet.getContent(), session.getUser(), channel);

    if (result == null || result.trim().isEmpty()) return;

    if (packet.getContent().startsWith("!help")) {
      targetSessions = Set.of(session.getId());
    }

    for (UUID targetSessionId : targetSessions) {
      ByteArrayOutputStream commandStream = new ByteArrayOutputStream();
      packetWriter.writePacket(
          commandStream,
          new pe.nanamochi.banchus.packets.server.MessagePacket(
              "BanchoBot", result, packet.getTarget(), 1));
      packetBundleService.enqueue(targetSessionId, new PacketBundle(commandStream.toByteArray()));
    }
  }
}
