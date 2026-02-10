package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.client.StatusUpdateRequestPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.UserStatsPacket;
import pe.nanamochi.banchus.services.player.RankingService;
import pe.nanamochi.banchus.services.player.StatService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(value = Packets.OSU_STATUS_UPDATE_REQUEST, checkForRestriction = true)
public class StatusUpdateRequestHandler extends AbstractPacketHandler<StatusUpdateRequestPacket> {
  private static final Logger logger = LoggerFactory.getLogger(StatusUpdateRequestHandler.class);

  private final StatService statService;
  private final RankingService rankingService;
  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;

  @Override
  public void handle(
      StatusUpdateRequestPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    Stat ownStats = statService.getStats(session.getUser(), session.getGamemode());

    int ownGlobalRank =
        Math.toIntExact(rankingService.getGlobalRank(session.getGamemode(), session.getUser()));

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
            (float) ownStats.getAccuracy(),
            ownStats.getPlayCount(),
            ownStats.getTotalScore(),
            ownGlobalRank,
            ownStats.getPerformancePoints()));
    packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
  }
}
