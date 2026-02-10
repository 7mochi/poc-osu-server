package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.Mods;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.client.UserStatusPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.UserStatsPacket;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.player.RankingService;
import pe.nanamochi.banchus.services.player.StatService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(value = Packets.OSU_USER_STATUS, checkForRestriction = true)
public class UserStatusHandler extends AbstractPacketHandler<UserStatusPacket> {
  private static final Logger logger = LoggerFactory.getLogger(UserStatusHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final StatService statService;
  private final SessionService sessionService;
  private final RankingService rankingService;

  @Override
  public void handle(UserStatusPacket packet, Session session, ByteArrayOutputStream responseStream)
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

    Stat ownStats = statService.getStats(session.getUser(), packet.getMode());
    int globalRank =
        Math.toIntExact(rankingService.getGlobalRank(packet.getMode(), session.getUser()));

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
              (float) ownStats.getAccuracy(),
              ownStats.getPlayCount(),
              ownStats.getTotalScore(),
              globalRank,
              ownStats.getPerformancePoints()));
      packetBundleService.enqueue(otherSession.getId(), new PacketBundle(stream.toByteArray()));
    }
  }
}
