package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.MatchStatus;
import pe.nanamochi.banchus.entities.commons.MatchType;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.packets.Match;
import pe.nanamochi.banchus.entities.packets.MatchSlot;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.client.MatchJoinPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_JOIN)
public class MatchJoinHandler extends AbstractPacketHandler<MatchJoinPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchJoinHandler.class);

  private final PacketWriter packetWriter;
  private final PacketBundleService packetBundleService;
  private final SessionService sessionService;
  private final MultiplayerService multiplayerService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  public void handle(MatchJoinPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    // Attempt to find the match we are trying to join
    MultiplayerMatch match = multiplayerService.findById(packet.getMatchId());

    if (match == null) {
      logger.warn(
          "User {} tried to join a non-existing match with ID {}",
          session.getUser().getUsername(),
          packet.getMatchId());
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new MatchJoinFailPacket());
      packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
      return;
    }

    if (session.getUser().isRestricted()) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new MatchJoinFailPacket());
      packetWriter.writePacket(
          stream, new AnnouncePacket("Multiplayer is not available while restricted."));
      packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
      logger.warn(
          "A restricted user ({}) attempted to join a multiplayer match.",
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
          "A silenced user ({}) attempted to join a multiplayer match.",
          session.getUser().getUsername());
      return;
    }

    // If the match has a non-empty password, validate the client got it right
    if (!match.getMatchPassword().isEmpty()
        && !match.getMatchPassword().equals(packet.getMatchPassword())) {
      logger.warn(
          "User {} tried to join a match with an incorrect password",
          session.getUser().getUsername());
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      packetWriter.writePacket(stream, new MatchJoinFailPacket());
      packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
      return;
    }

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

    Channel matchChannel = channelService.findByName("#mp_" + match.getMatchId());

    // Join the #multiplayer channel
    channelMembersService.addMemberToChannel(matchChannel, session);
    Set<UUID> matchChannelMembersId = channelMembersService.getMembers(matchChannel.getId());

    // Inform our user of the #multiplayer channel
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    packetWriter.writePacket(
        stream,
        new ChannelAvailableAutoJoinPacket(
            "#multiplayer", matchChannel.getTopic(), matchChannelMembersId.size()));
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

    // Make other people aware the session joined
    matchBroadcastService.broadcastMatchUpdates(match.getMatchId(), true, List.of());

    logger.info("User {} joined match {}", session.getUser().getUsername(), match.getMatchId());
  }
}
