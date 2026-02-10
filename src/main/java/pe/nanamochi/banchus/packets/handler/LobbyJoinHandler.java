package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.MatchStatus;
import pe.nanamochi.banchus.entities.commons.MatchType;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.packets.Match;
import pe.nanamochi.banchus.entities.packets.MatchSlot;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.client.LobbyJoinPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.MatchUpdatePacket;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_LOBBY_JOIN)
public class LobbyJoinHandler extends AbstractPacketHandler<LobbyJoinPacket> {
  private static final Logger logger = LoggerFactory.getLogger(LobbyJoinHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final MultiplayerService multiplayerService;

  @Override
  public void handle(LobbyJoinPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    session.setReceiveMatchUpdates(true);
    session = sessionService.updateSession(session);

    List<MultiplayerMatch> matches = multiplayerService.getAllMatches();

    for (MultiplayerMatch match : matches) {
      List<MultiplayerSlot> slots = multiplayerService.getAllSlots(match.getMatchId());
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      Match matchData = new Match();
      matchData.setId(match.getMatchId());
      matchData.setInProgress(match.getStatus() == MatchStatus.PLAYING);
      matchData.setType(MatchType.STANDARD);
      matchData.setMods(match.getMods());
      matchData.setName(match.getMatchName());
      matchData.setPassword(match.getMatchPassword());
      matchData.setBeatmapName(match.getBeatmapName());
      matchData.setBeatmapId(match.getBeatmapId());
      matchData.setBeatmapMd5(match.getBeatmapMd5());
      List<MatchSlot> matchSlotsData = new ArrayList<>(List.of());
      for (MultiplayerSlot slot : slots) {
        MatchSlot matchSlot = new MatchSlot();
        matchSlot.setUserId(slot.getUserId());
        matchSlot.setStatus(slot.getStatus());
        matchSlot.setTeam(slot.getTeam());
        matchSlot.setMods(slot.getMods());
        matchSlotsData.add(matchSlot);
      }
      matchData.setSlots(matchSlotsData);
      matchData.setHostId(match.getHostUserId());
      matchData.setMode(match.getMode());
      matchData.setScoringType(match.getScoringType());
      matchData.setTeamType(match.getTeamType());
      matchData.setFreemodsEnabled(match.isFreemodsEnabled());
      matchData.setRandomSeed(match.getRandomSeed());

      packetWriter.writePacket(stream, new MatchUpdatePacket(matchData, false));

      packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
    }
  }
}
