package pe.nanamochi.banchus.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.commons.Mods;
import pe.nanamochi.banchus.entities.commons.SlotStatus;
import pe.nanamochi.banchus.entities.commons.SlotTeam;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.entities.redis.MultiplayerMatch;
import pe.nanamochi.banchus.entities.redis.MultiplayerSlot;
import pe.nanamochi.banchus.entities.redis.PacketBundle;
import pe.nanamochi.banchus.packets.core.PacketWriter;
import pe.nanamochi.banchus.packets.server.ChannelRevokedPacket;
import pe.nanamochi.banchus.packets.server.MatchDisbandPacket;
import pe.nanamochi.banchus.packets.server.SilenceInfoPacket;
import pe.nanamochi.banchus.packets.server.UserSilencedPacket;
import pe.nanamochi.banchus.services.auth.SessionService;
import pe.nanamochi.banchus.services.communication.ChannelMembersService;
import pe.nanamochi.banchus.services.communication.ChannelService;
import pe.nanamochi.banchus.services.multiplayer.MatchBroadcastService;
import pe.nanamochi.banchus.services.multiplayer.MultiplayerService;
import pe.nanamochi.banchus.services.player.SilenceService;
import pe.nanamochi.banchus.services.player.UserService;
import pe.nanamochi.banchus.services.protocol.PacketBundleService;

@Component
@Command(name = "silence", documentation = "Silence a user for a specified duration.")
@RequiredArgsConstructor
public class SilenceCommand extends BaseCommand {
  private static final Logger logger = LoggerFactory.getLogger(SilenceCommand.class);
  private final UserService userService;
  private final SilenceService silenceService;
  private final PacketWriter packetWriter;
  private final SessionService sessionService;
  private final MultiplayerService multiplayerService;
  private final ChannelService channelService;
  private final ChannelMembersService channelMembersService;
  private final PacketBundleService packetBundleService;
  private final MatchBroadcastService matchBroadcastService;

  @Override
  String processCommand(User user, String trigger, String[] args) throws IOException {
    logger.info("Processing command silence");
    if (args.length < 2) {
      return "Not enough arguments. Usage: !silence <username> <duration>";
    }

    User targetUser = userService.findByUsername(args[0]);
    if (targetUser == null) {
      return "Username not found.";
    }

    if (targetUser.isRestricted()) {
      return "Username not found.";
    }

    if (targetUser.isSilenced()) {
      return "User is already silenced.";
    }

    Session targetSession = sessionService.getPrimarySessionByUserId(targetUser.getId());

    if (targetUser.getId() == user.getId()) {
      return "You cannot silence yourself.";
    }

    if (targetUser.getId() == 1) {
      return "You cannot silence the bot.";
    }

    String durationInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
    Instant silencedUntil = silenceService.calculateSilenceUntil(durationInput);

    if (silencedUntil == null) {
      return "Invalid duration format. Please specify a valid duration (e.g., 10m, 1h, 2d, 5 days,"
          + " 1 year, etc).";
    }

    targetUser.setSilenceEnd(silencedUntil);
    userService.updateUser(targetUser);

    // Inform the user's client
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    long secondsRemainingSilence =
        Duration.between(Instant.now(), targetUser.getSilenceEnd()).toSeconds();
    packetWriter.writePacket(
        stream, new SilenceInfoPacket(Math.toIntExact(secondsRemainingSilence)));

    if (targetSession != null) {
      packetBundleService.enqueue(targetSession.getId(), new PacketBundle(stream.toByteArray()));
    }

    // Wipe their messages from any channel
    stream = new ByteArrayOutputStream();
    packetWriter.writePacket(stream, new UserSilencedPacket(targetUser.getId()));

    List<Session> otherSession = sessionService.getAllSessions();
    for (Session session : otherSession) {
      packetBundleService.enqueue(session.getId(), new PacketBundle(stream.toByteArray()));
    }

    // Remove them from multiplayer match (if any)
    if (targetSession.getMultiplayerMatchId() != null
        && targetSession.getMultiplayerMatchId() != -1) {
      MultiplayerMatch match = multiplayerService.findById(targetSession.getMultiplayerMatchId());

      // Fetch our slot
      MultiplayerSlot slot =
          multiplayerService.findSlotBySessionId(match.getMatchId(), targetSession.getId());

      // Remove us from the match
      slot.setUserId(-1);
      slot.setSessionId(null);
      slot.setStatus(SlotStatus.OPEN.getValue());
      slot.setTeam(SlotTeam.NEUTRAL);
      slot.setMods(Mods.NO_MOD.getValue());
      slot.setLoaded(false);
      slot.setSkipped(false);
      multiplayerService.updateSlot(match.getMatchId(), slot);

      Channel matchChannel = channelService.findByName("#mp_" + match.getMatchId());
      if (match.getHostUserId() == targetSession.getUser().getId()) {
        // If the host left, pick a new host
        List<MultiplayerSlot> slots = multiplayerService.getAllSlots(match.getMatchId());

        MultiplayerSlot newHostSlot = null;
        for (MultiplayerSlot s : slots) {
          // Slot doesn't have a user
          if (slot.getUserId() == -1) continue;
          newHostSlot = s;
          break;
        }

        // No one is left in the match, close it
        if (newHostSlot == null) {
          stream = new ByteArrayOutputStream();
          packetWriter.writePacket(stream, new MatchDisbandPacket(match.getMatchId()));
          matchBroadcastService.broadcastToLobby(stream.toByteArray());

          channelMembersService.removeMemberFromChannel(matchChannel, targetSession);
          stream = new ByteArrayOutputStream();
          packetWriter.writePacket(stream, new MatchDisbandPacket(match.getMatchId()));
          packetWriter.writePacket(stream, new ChannelRevokedPacket("#multiplayer"));
          packetBundleService.enqueue(
              targetSession.getId(), new PacketBundle(stream.toByteArray()));

          // Delete the multiplayer match (and it's slots)
          match = multiplayerService.delete(match.getMatchId());

          // Delete the #multiplayer channel
          channelService.delete(matchChannel);

          logger.info(
              "Multiplayer match {} closed due to no remaining players.", match.getMatchId());
        }
      }
    }

    return String.format(
        "User %s has been silenced for %s.",
        targetUser.getUsername(), silenceService.formatRemainingSilence(silencedUntil));
  }
}
