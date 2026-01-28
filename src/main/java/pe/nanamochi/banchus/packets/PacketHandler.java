package pe.nanamochi.banchus.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.Mods;
import pe.nanamochi.banchus.entities.PacketBundle;
import pe.nanamochi.banchus.entities.QuitState;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.packets.client.*;
import pe.nanamochi.banchus.packets.server.ChannelAvailablePacket;
import pe.nanamochi.banchus.packets.server.ChannelJoinSuccessPacket;
import pe.nanamochi.banchus.packets.server.UserQuitPacket;
import pe.nanamochi.banchus.packets.server.UserStatsPacket;
import pe.nanamochi.banchus.services.ChannelMembersRedisService;
import pe.nanamochi.banchus.services.ChannelService;
import pe.nanamochi.banchus.services.PacketBundleService;
import pe.nanamochi.banchus.services.SessionService;
import pe.nanamochi.banchus.services.StatService;

@Component
public class PacketHandler {

  private static final Logger logger = LoggerFactory.getLogger(PacketHandler.class);
  @Autowired private PacketWriter packetWriter;
  @Autowired private SessionService sessionService;
  @Autowired private StatService statService;
  @Autowired private PacketBundleService packetBundleService;
  @Autowired private ChannelService channelService;
  @Autowired private ChannelMembersRedisService channelMembersService;

  public void handlePacket(Packet packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (packet != null) {
      logger.debug("Handling packet: {}", packet.getPacketType());

      switch (packet.getPacketType()) {
        case OSU_USER_STATUS ->
            handleUserStatus((UserStatusPacket) packet, session, responseStream);
        case OSU_MESSAGE -> handleMessage((MessagePacket) packet, session, responseStream);
        case OSU_EXIT -> handleExit((ExitPacket) packet, session, responseStream);
        case OSU_STATUS_UPDATE_REQUEST ->
            handleStatusUpdateRequest((StatusUpdateRequestPacket) packet, session, responseStream);
        case OSU_PONG -> handlePong((PongPacket) packet, session, responseStream);
        case OSU_PRIVATE_MESSAGE ->
            handlePrivateMessage((PrivateMessagePacket) packet, session, responseStream);
        case OSU_RECEIVE_UPDATES ->
            handleReceiveUpdates((ReceiveUpdatesPacket) packet, session, responseStream);
        case OSU_USER_STATS_REQUEST ->
            handleUserStatsRequest((UserStatsRequestPacket) packet, session, responseStream);
        case OSU_CHANNEL_JOIN ->
            handleChannelJoin((ChannelJoinPacket) packet, session, responseStream);
        case OSU_CHANNEL_LEAVE ->
            handleChannelLeave((ChannelLeavePacket) packet, session, responseStream);
        case OSU_LOBBY_PART -> handleLobbyPart((LobbyPartPacket) packet, session, responseStream);
        default -> logger.warn("Packet {} not implemented yet.", packet.getPacketType());
      }
    }
  }

  private void handleUserStatus(
      UserStatusPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    // TODO: Check privileges

    // Filter invalid mod combinations, this is a quirk of the osu! client,
    // where it adjusts this value only after it sends the packet to the server,
    // so we need to adjust
    packet.setMods(Mods.filterInvalidModCombinations(packet.getMods(), packet.getMode()));

    session.setAction(packet.getAction().getValue());
    session.setInfoText(packet.getText());
    session.setBeatmapMd5(packet.getBeatmapChecksum());
    session.setMods(Mods.toBitmask(packet.getMods()));
    session.setGamemode(packet.getMode());
    session.setBeatmapId(packet.getBeatmapId());
    session = sessionService.updateSession(session);

    // TODO: Calculate global rank

    Stat ownStats = statService.getStats(session.getUser(), packet.getMode());

    // Send the stats update to all active osu sessions
    for (Session otherSession : sessionService.getAllSessions()) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(
          stream,
          new UserStatsPacket(
              session.getUser().getId(),
              session.getAction(),
              session.getInfoText(),
              session.getBeatmapMd5(),
              session.getMods(),
              session.getGamemode(),
              session.getBeatmapId(),
              ownStats.getRankedScore(),
              ownStats.getAccuracy(),
              ownStats.getPlayCount(),
              ownStats.getTotalScore(),
              727, // TODO: global rank
              ownStats.getPerformancePoints()));
      packetBundleService.enqueue(otherSession.getId(), new PacketBundle(stream.toByteArray()));
    }
  }

  private void handleMessage(
      MessagePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    // TODO: Validate silence

    String channelName = null;
    if (packet.getTarget().equals("#multiplayer")) {
      // TODO: Handle multiplayer chat
    } else if (packet.getTarget().equals("#spectator")) {
      // TODO: Handle spectator chat
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

    // If the user is restricted, they cannot send messages
    if (session.getUser().isRestricted()) return;

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

    // TODO: handle commands
  }

  private void handleExit(ExitPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
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

  private void handleStatusUpdateRequest(
      StatusUpdateRequestPacket packet, Session session, ByteArrayOutputStream responseStream) {}

  private void handlePong(
      PongPacket packet, Session session, ByteArrayOutputStream responseStream) {}

  private void handlePrivateMessage(
      PrivateMessagePacket packet, Session session, ByteArrayOutputStream responseStream) {}

  private void handleReceiveUpdates(
      ReceiveUpdatesPacket packet, Session session, ByteArrayOutputStream responseStream) {}

  private void handleUserStatsRequest(
      UserStatsRequestPacket packet, Session session, ByteArrayOutputStream responseStream) {}

  private void handleChannelJoin(
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

  private void handleChannelLeave(
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

  private void handleLobbyPart(
      LobbyPartPacket packet, Session session, ByteArrayOutputStream responseStream)
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
