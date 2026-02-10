package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.packets.client.MatchScoreUpdatePacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_SCORE_UPDATE)
public class MatchScoreUpdateHandler extends AbstractPacketHandler<MatchScoreUpdatePacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchScoreUpdateHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;
  private final PacketWriter packetWriter;

  @Override
  public void handle(
      MatchScoreUpdatePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} sent a match score frame but they are not in a match.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());
    if (match == null) {
      logger.warn(
          "User {} sent a match score frame but their match does not exist.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerSlot slot =
        multiplayerService.findSlotBySessionId(match.getMatchId(), session.getId());
    if (slot == null) {
      logger.warn(
          "User {} sent a match score frame but they are not in a slot.",
          session.getUser().getUsername());
      return;
    }

    packet.getFrame().setId(slot.getSlotId());

    ByteArrayOutputStream packetStream = new ByteArrayOutputStream();
    packetWriter.writePacket(
        packetStream,
        new pe.nanamochi.banchus.packets.server.MatchScoreUpdatePacket(packet.getFrame()));

    matchBroadcastService.broadcastToMatch(
        match.getMatchId(), packetStream.toByteArray(), SlotStatus.PLAYING.getValue());
  }
}
