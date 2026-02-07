package pe.nanamochi.banchus.packets.client.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.Packets;
import pe.nanamochi.banchus.packets.client.StatusUpdateRequestPacket;
import pe.nanamochi.banchus.packets.server.UserStatsPacket;
import pe.nanamochi.banchus.services.PacketBundleService;
import pe.nanamochi.banchus.services.RankingService;
import pe.nanamochi.banchus.services.StatService;

@Component
@RequiredArgsConstructor
public class StatusUpdateRequestHandler extends AbstractPacketHandler<StatusUpdateRequestPacket> {
  private static final Logger logger = LoggerFactory.getLogger(StatusUpdateRequestHandler.class);

  private final StatService statService;
  private final RankingService rankingService;
  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;

  @Override
  public boolean isRestricted() {
    return true;
  }

  @Override
  public Packets getPacketType() {
    return Packets.OSU_STATUS_UPDATE_REQUEST;
  }

  @Override
  public Class<StatusUpdateRequestPacket> getPacketClass() {
    return StatusUpdateRequestPacket.class;
  }

  @Override
  public void handle(
      StatusUpdateRequestPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    logger.debug("Handling packet: {}", getPacketType());

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
            ownStats.getAccuracy(),
            ownStats.getPlayCount(),
            ownStats.getTotalScore(),
            ownGlobalRank,
            ownStats.getPerformancePoints()));
    packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
  }
}
