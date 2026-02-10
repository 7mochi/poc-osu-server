package pe.nanamochi.banchus.services.multiplayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.commons.MatchStatus;
import pe.nanamochi.banchus.entities.commons.MatchType;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.packets.Match;
import pe.nanamochi.banchus.entities.packets.MatchSlot;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.server.MatchUpdatePacket;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Service
@RequiredArgsConstructor
public class MatchBroadcastService {
  private static final Logger logger = LoggerFactory.getLogger(MatchBroadcastService.class);

  private final MultiplayerService multiplayerService;
  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;

  public void broadcastMatchUpdates(int matchId, boolean sendToLobby, List<UUID> extraSessionIds)
      throws IOException {
    MultiplayerMatch match = multiplayerService.findById(matchId);
    List<MultiplayerSlot> slots = multiplayerService.getAllSlots(match.getMatchId());

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
    List<MatchSlot> slotsData = new ArrayList<>();
    for (MultiplayerSlot s : slots) {
      MatchSlot slotData = new MatchSlot();
      slotData.setUserId(s.getUserId());
      slotData.setStatus(s.getStatus());
      slotData.setTeam(s.getTeam());
      slotData.setMods(match.isFreemodsEnabled() ? s.getMods() : 0);
      slotsData.add(slotData);
    }
    matchData.setSlots(slotsData);
    matchData.setHostId(match.getHostUserId());
    matchData.setMode(match.getMode());
    matchData.setScoringType(match.getScoringType());
    matchData.setTeamType(match.getTeamType());
    matchData.setFreemodsEnabled(match.isFreemodsEnabled());
    matchData.setRandomSeed(match.getRandomSeed());

    // Send the match data (with password) to those to the multiplayer match
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(stream, new MatchUpdatePacket(matchData, true));

    for (UUID sessionId : extraSessionIds) {
      packetBundleService.enqueue(sessionId, new PacketBundle(stream.toByteArray()));
    }

    broadcastToMatch(matchId, stream.toByteArray(), SlotStatus.HAS_PLAYER.getValue());

    if (sendToLobby) {
      stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new MatchUpdatePacket(matchData, false));

      broadcastToLobby(stream.toByteArray());
    }
  }

  public void broadcastToMatch(int matchId, byte[] data, int slotFlags) {
    MultiplayerMatch match = multiplayerService.findById(matchId);
    List<MultiplayerSlot> slots = multiplayerService.getAllSlots(match.getMatchId());

    for (MultiplayerSlot slot : slots) {
      if (slot.getUserId() == -1 || (slot.getStatus() & slotFlags) == 0) continue;

      packetBundleService.enqueue(slot.getSessionId(), new PacketBundle(data));
    }
  }

  public void broadcastToLobby(byte[] data) {
    Channel lobbyChannel = channelService.findByName("#lobby");
    if (lobbyChannel == null) {
      logger.warn("Failed to fetch #lobby channel.");
      return;
    }

    for (UUID sessionId : channelMembersService.getMembers(lobbyChannel.getId())) {
      packetBundleService.enqueue(sessionId, new PacketBundle(data));
    }
  }
}
