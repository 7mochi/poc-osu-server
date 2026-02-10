package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.packets.client.MatchChangePasswordPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_CHANGE_PASSWORD)
public class MatchChangePasswordHandler extends AbstractPacketHandler<MatchChangePasswordPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchChangePasswordHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  public void handle(
      MatchChangePasswordPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} attempted to change match password but is not in a match",
          session.getUser().getUsername());
      return;
    }

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());
    if (match == null) {
      logger.warn(
          "User {} attempted to change the match password but their match doesn't exist",
          session.getUser().getUsername());
      return;
    }

    if (match.getHostUserId() != session.getUser().getId()) {
      logger.warn(
          "User {} attempted to change the match password but is not the host",
          session.getUser().getUsername());
      return;
    }

    match.setMatchPassword(packet.getMatch().getPassword());
    multiplayerService.update(match);

    matchBroadcastService.broadcastMatchUpdates(session.getMultiplayerMatchId(), true, List.of());

    logger.info(
        "User {} updated the match password for match {}",
        session.getUser().getUsername(),
        session.getMultiplayerMatchId());
  }
}
