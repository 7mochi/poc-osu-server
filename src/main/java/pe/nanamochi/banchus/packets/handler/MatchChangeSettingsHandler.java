package pe.nanamochi.banchus.packets.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.MatchTeamType;
import pe.nanamochi.banchus.entities.commons.Mods;
import pe.nanamochi.banchus.entities.commons.SlotTeam;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.packets.client.MatchChangeSettingsPacket;
import pe.nanamochi.banchus.packets.core.AbstractPacketHandler;
import pe.nanamochi.banchus.packets.core.HandleClientPacket;
import pe.nanamochi.banchus.packets.core.Packets;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;

@Component
@RequiredArgsConstructor
@HandleClientPacket(Packets.OSU_MATCH_CHANGE_SETTINGS)
public class MatchChangeSettingsHandler extends AbstractPacketHandler<MatchChangeSettingsPacket> {
  private static final Logger logger = LoggerFactory.getLogger(MatchChangeSettingsHandler.class);

  private final MultiplayerService multiplayerService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  public void handle(
      MatchChangeSettingsPacket packet, Session session, ByteArrayOutputStream responseStream)
      throws IOException {
    if (session.getMultiplayerMatchId() == null) {
      logger.warn(
          "User {} tried to change match settings while not in a match.",
          session.getUser().getUsername());
      return;
    }

    MultiplayerMatch match = multiplayerService.findById(session.getMultiplayerMatchId());

    // Only the host can change match settings
    if (match.getHostUserId() != session.getUser().getId()) {
      logger.warn(
          "User {} tried to change match settings but is not the host.",
          session.getUser().getUsername());
      return;
    }

    List<MultiplayerSlot> slots = multiplayerService.getAllSlots(session.getMultiplayerMatchId());
    boolean needSlotupdates = false;
    // If we switch to a versus mode, split all players into teams
    if (packet.getMatch().getTeamType() != match.getTeamType()
        && (packet.getMatch().getTeamType() == MatchTeamType.TEAM_VS
            || packet.getMatch().getTeamType() == MatchTeamType.TAG_TEAM_VS)) {
      needSlotupdates = true;
      int i = 0;
      for (MultiplayerSlot slot : slots) {
        if (slot.getUserId() == -1) continue;

        if (i % 2 != 0) {
          slot.setTeam(SlotTeam.BLUE);
        } else {
          slot.setTeam(SlotTeam.RED);
        }

        i += 1;
      }
    }

    // if freemod is activated the match mods are transferred to the slots
    // if freemod is disabled the mods will clear
    if (packet.getMatch().isFreemodsEnabled() != match.isFreemodsEnabled()) {
      int mods = Mods.NO_MOD.getValue();
      // Copy bancho behavior
      if (packet.getMatch().isFreemodsEnabled()) {
        mods = match.getMods() & (~Mods.SPEED_CHANGING);
        packet.getMatch().setMods(match.getMods() & (Mods.SPEED_CHANGING));
      }

      needSlotupdates = true;
      for (MultiplayerSlot slot : slots) {
        if (slot.getUserId() != -1) {
          slot.setMods(mods);
        }
      }
    }

    // Update slots if needed
    if (needSlotupdates) {
      for (MultiplayerSlot slot : slots) {
        if (slot.getUserId() != -1) {
          multiplayerService.updateSlot(match.getMatchId(), slot);
        }
      }
    }

    // Update match
    match.setMatchName(packet.getMatch().getName());
    match.setMatchPassword(packet.getMatch().getPassword());
    match.setBeatmapName(packet.getMatch().getBeatmapName());
    match.setBeatmapId(packet.getMatch().getBeatmapId());
    match.setBeatmapMd5(packet.getMatch().getBeatmapMd5());
    match.setMode(packet.getMatch().getMode());
    match.setMods(packet.getMatch().getMods());
    match.setScoringType(packet.getMatch().getScoringType());
    match.setTeamType(packet.getMatch().getTeamType());
    match.setFreemodsEnabled(packet.getMatch().isFreemodsEnabled());
    match.setRandomSeed(packet.getMatch().getRandomSeed());
    match = multiplayerService.update(match);

    // Inform relevant places of the new match state
    matchBroadcastService.broadcastMatchUpdates(match.getMatchId(), true, List.of());

    logger.info(
        "User {} changed settings for match {}.",
        session.getUser().getUsername(),
        match.getMatchId());
  }
}
