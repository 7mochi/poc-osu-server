package pe.nanamochi.banchus.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.Mods;
import pe.nanamochi.banchus.entities.PacketBundle;
import pe.nanamochi.banchus.entities.QuitState;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.packets.client.*;
import pe.nanamochi.banchus.packets.server.UserQuitPacket;
import pe.nanamochi.banchus.packets.server.UserStatsPacket;
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
      MessagePacket packet, Session session, ByteArrayOutputStream responseStream) {}

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

    // TODO: Leave channels the osu session is in

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
}
