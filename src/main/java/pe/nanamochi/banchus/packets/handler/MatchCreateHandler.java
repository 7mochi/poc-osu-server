package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.nanamochi.banchus.entities.commons.MatchStatus;
import pe.nanamochi.banchus.entities.commons.MatchType;
import pe.nanamochi.banchus.entities.commons.ServerPrivileges;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.packets.Match;
import pe.nanamochi.banchus.entities.packets.MatchSlot;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.client.MatchCreatePacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.gameplay.SpectatorService;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@HandleClientPacket(Packets.OSU_MATCH_CREATE)
@RequiredArgsConstructor
public class MatchCreateHandler extends AbstractPacketHandler<MatchCreatePacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchCreateHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final MultiplayerService multiplayerService;
  private final SpectatorService spectatorService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  public void handle(
      MatchCreatePacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getUser().isRestricted()) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new MatchJoinFailPacket());
      packetWriter.writePacket(
          stream, new AnnouncePacket("Multiplayer is not available while restricted."));
      packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
      logger.warn(
          "A restricted user ({}) attempted to create a multiplayer match.",
          session.getUser().getUsername());
      return;
    }

    if (session.getUser().isSilenced()) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new MatchJoinFailPacket());
      packetWriter.writePacket(
          stream, new AnnouncePacket("Multiplayer is not available while silenced."));
      packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
      logger.warn(
          "A silenced user ({}) attempted to create a multiplayer match.",
          session.getUser().getUsername());
      return;
    }

    // If we are spectating someone, stop spectating them
    if (session.getSpectatorHostSessionId() != null) {
      Session hostSession = sessionService.getSessionByID(session.getSpectatorHostSessionId());

      if (hostSession == null) {
        logger.warn(
            "A user ({}) attempted to stop spectating another user who is offline.",
            session.getUser().getUsername());
        return;
      }

      // Remove us from the host's spectators
      spectatorService.remove(hostSession.getId(), session.getId());

      // Remote the host from our presence
      session.setSpectatorHostSessionId(null);
      session = sessionService.updateSession(session);

      // Inform the host that we left
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new SpectatorLeftPacket(session.getUser().getId()));
      packetBundleService.enqueue(hostSession.getId(), new PacketBundle(stream.toByteArray()));

      // Inform the other spectators that we left
      for (UUID spectatorSessionId : spectatorService.getMembers(hostSession.getId())) {
        if (spectatorSessionId.equals(session.getId())) {
          continue;
        }

        stream = new ByteArrayOutputStream();
        packetWriter.writePacket(stream, new FellowSpectatorLeftPacket(session.getUser().getId()));
        packetBundleService.enqueue(spectatorSessionId, new PacketBundle(stream.toByteArray()));
      }
    }

    // Create the multiplayer match
    MultiplayerMatch match = new MultiplayerMatch();
    match.setMatchName(packet.getMatch().getName());
    match.setMatchPassword(packet.getMatch().getPassword());
    match.setBeatmapName(packet.getMatch().getBeatmapName());
    match.setBeatmapId(packet.getMatch().getBeatmapId());
    match.setBeatmapMd5(packet.getMatch().getBeatmapMd5());
    match.setHostUserId(session.getUser().getId());
    match.setMode(packet.getMatch().getMode());
    match.setMods(packet.getMatch().getMods());
    match.setScoringType(packet.getMatch().getScoringType());
    match.setTeamType(packet.getMatch().getTeamType());
    match.setFreemodsEnabled(packet.getMatch().isFreemodsEnabled());
    match.setRandomSeed(packet.getMatch().getRandomSeed());
    match = multiplayerService.create(match);

    // Create the multiplayer chat
    Channel matchChannel = new Channel();
    matchChannel.setName("#mp_" + match.getMatchId());
    matchChannel.setTopic("Channel for multiplayer match ID " + match.getMatchId());
    matchChannel.setReadPrivileges(ServerPrivileges.UNRESTRICTED.getValue());
    matchChannel.setWritePrivileges(ServerPrivileges.UNRESTRICTED.getValue());
    matchChannel.setAutoJoin(false);
    matchChannel.setTemporary(true);
    matchChannel = channelService.save(matchChannel);

    // Claim a slot for the session
    Integer slotId = multiplayerService.claimSlotId(match.getMatchId());
    if (slotId == null) {
      logger.error("Failed to claim slot ID for multiplayer match ID {}", match.getMatchId());
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new MatchJoinFailPacket());
      packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
      return;
    }

    MultiplayerSlot slot = multiplayerService.findSlotById(match.getMatchId(), slotId);
    slot.setUserId(session.getUser().getId());
    slot.setSessionId(session.getId());
    slot.setStatus(SlotStatus.NOT_READY.getValue());
    multiplayerService.updateSlot(match.getMatchId(), slot);

    // Join the multiplayer match
    session.setMultiplayerMatchId(match.getMatchId());
    session = sessionService.updateSession(session);

    // Join the #multiplayer channel
    channelMembersService.addMemberToChannel(matchChannel, session);

    // Inform our user of the #multiplayer channel
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(
        stream, new ChannelAvailableAutoJoinPacket("#multiplayer", matchChannel.getTopic(), 1));
    packetWriter.writePacket(stream, new ChannelJoinSuccessPacket("#multiplayer"));
    packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));

    List<MultiplayerSlot> slots = multiplayerService.getAllSlots(match.getMatchId());

    // Send the match data (with password) to the creator
    stream = new ByteArrayOutputStream();
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

    packetWriter.writePacket(stream, new MatchJoinSuccessPacket(matchData, true));
    packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));

    match.setHostUserId(session.getUser().getId());
    match = multiplayerService.update(match);

    matchBroadcastService.broadcastMatchUpdates(match.getMatchId(), true, List.of());
  }
}
