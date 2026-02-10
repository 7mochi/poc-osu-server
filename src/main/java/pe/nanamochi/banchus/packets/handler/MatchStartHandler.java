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
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.packets.Match;
import pe.nanamochi.banchus.entities.packets.MatchSlot;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.packets.client.MatchStartPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_START)
public class MatchStartHandler extends AbstractPacketHandler<MatchStartPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchStartHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;
  private final PacketWriter packetWriter;

  @Override
  public void handle(MatchStartPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} tried to start a match but they aren't in a match.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());
    if (match == null) {
      logger.warn(
          "User {} tried to start a match but their match doesn't exist.",
          session.getUser().getUsername());
      return;
    }

    if (match.getHostUserId() != session.getUser().getId()) {
      logger.warn(
          "User {} tried to start a match but they aren't the host.",
          session.getUser().getUsername());
      return;
    }

    match.setStatus(MatchStatus.PLAYING);
    match = multiplayerService.update(match);

    List<MultiplayerSlot> slots = multiplayerService.getAllSlots(match.getMatchId());
    for (int i = 0; i < slots.size(); i++) {
      MultiplayerSlot slot = slots.get(i);
      if ((slot.getStatus() & SlotStatus.CAN_START.getValue()) != 0) {
        slot.setStatus(SlotStatus.PLAYING.getValue());
        MultiplayerSlot updatedSlot = multiplayerService.updateSlot(match.getMatchId(), slot);
        slots.set(i, updatedSlot);
      }
    }
    Match matchData = new Match();
    matchData.setId(match.getMatchId());
    matchData.setInProgress(match.getStatus() == MatchStatus.PLAYING);
    matchData.setMods(match.getMods());
    matchData.setName(match.getMatchName());
    matchData.setPassword(match.getMatchPassword());
    matchData.setBeatmapName(match.getBeatmapName());
    matchData.setBeatmapId(match.getBeatmapId());
    matchData.setBeatmapMd5(match.getBeatmapMd5());
    List<MatchSlot> slotsData = new ArrayList<>();
    for (MultiplayerSlot s : slots) {
      MatchSlot slotData = new MatchSlot();
      slotData.setUserId(s.getUserId());
      slotData.setStatus(s.getStatus());
      slotData.setTeam(s.getTeam());
      slotData.setMods(s.getMods());
      slotsData.add(slotData);
    }
    matchData.setSlots(slotsData);
    matchData.setHostId(match.getHostUserId());
    matchData.setMode(match.getMode());
    matchData.setScoringType(match.getScoringType());
    matchData.setTeamType(match.getTeamType());
    matchData.setFreemodsEnabled(match.isFreemodsEnabled());
    matchData.setRandomSeed(match.getRandomSeed());

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(
        stream, new pe.nanamochi.banchus.packets.server.MatchStartPacket(matchData, false));

    matchBroadcastService.broadcastToMatch(
        match.getMatchId(), stream.toByteArray(), SlotStatus.PLAYING.getValue());
    matchBroadcastService.broadcastToLobby(stream.toByteArray());

    logger.info(
        "User {} started multiplayer match {}.",
        session.getUser().getUsername(),
        match.getMatchId());
  }
}
