package pe.nanamochi.banchus.controllers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.nanamochi.banchus.entities.*;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.PacketHandler;
import pe.nanamochi.banchus.packets.PacketReader;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.*;
import pe.nanamochi.banchus.utils.IPApi;
import pe.nanamochi.banchus.utils.PrivilegesUtil;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class LoginController {
  private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

  private final PacketWriter packetWriter;
  private final PacketReader packetReader;
  private final PacketHandler packetHandler;
  private final UserService userService;
  private final SessionService sessionService;
  private final StatService statService;
  private final ChannelService channelService;
  private final ChannelMembersRedisService channelMembersRedisService;
  private final PacketBundleService packetBundleService;
  private final RankingService rankingService;

  @PostMapping(value = "/", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<Resource> banchoHandler(
      @RequestHeader HttpHeaders headers, @RequestBody byte[] data) throws IOException {
    ResponseEntity<Resource> response;

    if (!headers.containsHeader("osu-token")) {
      logger.debug("Handling login request");
      response = handleLogin(headers, new String(data, StandardCharsets.UTF_8));
    } else {
      logger.debug("Handling bancho request");
      response = handleBanchoRequest(headers, data);
    }

    return response;
  }

  private ResponseEntity<Resource> handleLogin(HttpHeaders headers, String data)
      throws IOException {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    LoginData loginData = new LoginData(data);
    InetAddress ipAddress;

    HttpHeaders responseHeaders = new HttpHeaders();
    String choToken;

    if (!headers.containsHeader("X-Real-IP")) {
      packetWriter.writePacket(stream, new LoginReplyPacket(-1));
      packetWriter.writePacket(stream, new AnnouncePacket("Could not determine your IP address."));

      responseHeaders.add("cho-token", "no");
      return ResponseEntity.ok()
          .headers(responseHeaders)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(new ByteArrayResource(stream.toByteArray()));
    } else {
      ipAddress = InetAddress.getByName(headers.getFirst("X-Real-IP"));
    }

    User user = userService.login(loginData.getUsername(), loginData.getPasswordMd5());
    Geolocation geolocation;

    if (ipAddress.isAnyLocalAddress() || ipAddress.isLoopbackAddress()) {
      geolocation = new Geolocation();
      geolocation.setLat(0.0f);
      geolocation.setLon(0.0f);
      geolocation.setCountryCode("kp");
    } else {
      geolocation = IPApi.fetchFromIP(ipAddress);
    }

    if (user != null) {
      Session otherOwnOsuSession =
          sessionService.getPrimarySessionByUsername(loginData.getUsername());

      Session ownOsuSession = new Session();
      ownOsuSession.setUser(user);
      ownOsuSession.setUtcOffset(Integer.parseInt(loginData.getUtcOffset()));
      ownOsuSession.setGamemode(Mode.OSU);
      ownOsuSession.setCountry(geolocation.getCountryCode().toLowerCase());
      ownOsuSession.setLatitude(geolocation.getLat());
      ownOsuSession.setLongitude(geolocation.getLon());
      ownOsuSession.setDisplayCityLocation(Boolean.parseBoolean(loginData.getDisplayCity()));
      ownOsuSession.setAction(0);
      ownOsuSession.setInfoText("");
      ownOsuSession.setBeatmapMd5("");
      ownOsuSession.setBeatmapId(0);
      ownOsuSession.setMods(Mods.toBitmask(List.of(Mods.NO_MOD)));
      ownOsuSession.setPmPrivate(Boolean.parseBoolean(loginData.getPmPrivate()));
      ownOsuSession.setReceiveMatchUpdates(false);
      ownOsuSession.setSpectatorHostSessionId(null);
      ownOsuSession.setAwayMessage("");
      ownOsuSession.setMultiplayerMatchId(-1);
      ownOsuSession.setLastCommunicatedAt(Instant.now());
      ownOsuSession.setLastNpBeatmapId(-1);
      ownOsuSession.setPrimarySession(
          otherOwnOsuSession == null || loginData.getOsuVersion().endsWith("tourney"));
      ownOsuSession.setOsuVersion(loginData.getOsuVersion());
      ownOsuSession.setOsuPathMd5(loginData.getOsuPathMd5());
      ownOsuSession.setAdaptersStr(loginData.getAdaptersStr());
      ownOsuSession.setAdaptersMd5(loginData.getAdaptersMd5());
      ownOsuSession.setUninstallMd5(loginData.getUninstallMd5());
      ownOsuSession.setDiskSignatureMd5(loginData.getDiskSignatureMd5());
      ownOsuSession = sessionService.saveSession(ownOsuSession);

      Stat ownStats = statService.getStats(user, ownOsuSession.getGamemode());
      int ownGlobalRank =
          Math.toIntExact(rankingService.getGlobalRank(ownOsuSession.getGamemode(), user));

      if (ownStats == null) {
        packetWriter.writePacket(stream, new LoginReplyPacket(-1));
        packetWriter.writePacket(stream, new AnnouncePacket("Own stats not found."));

        responseHeaders.add("cho-token", "no");
        return ResponseEntity.ok()
            .headers(responseHeaders)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new ByteArrayResource(stream.toByteArray()));
      }

      // Protocol version negotiation
      packetWriter.writePacket(stream, new ProtocolNegotiationPacket());

      // Login reply
      packetWriter.writePacket(stream, new LoginReplyPacket(user.getId()));

      // User privileges
      packetWriter.writePacket(
          stream,
          new LoginPermissionsPacket(
              PrivilegesUtil.serverToClientPrivileges(
                  user.getPrivileges() | ServerPrivileges.SUPPORTER.getValue())));

      // Chat channels
      List<Channel> autoJoinChannels = channelService.findByAutoJoin(true);
      for (Channel channel : autoJoinChannels) {
        if (!channelService.canReadChannel(channel, user.getPrivileges())
            || channel.getName().equals("#lobby")) {
          continue;
        }

        Set<UUID> currentChannelMembers = channelMembersRedisService.getMembers(channel.getId());
        packetWriter.writePacket(
            stream,
            new ChannelAvailablePacket(
                channel.getName(), channel.getTopic(), currentChannelMembers.size()));
      }

      // Notify the client that we're done sending channel info
      packetWriter.writePacket(stream, new ChannelInfoCompletePacket());

      // Send our presence and stats to ourselves
      packetWriter.writePacket(
          stream,
          new UserPresencePacket(
              user.getId(),
              user.getUsername(),
              ownOsuSession.getUtcOffset(),
              CountryCode.fromCode(ownOsuSession.getCountry()).getId(),
              0, // TODO: permissions
              ownOsuSession.getLatitude(),
              ownOsuSession.getLongitude(),
              ownGlobalRank));
      packetWriter.writePacket(
          stream,
          new UserStatsPacket(
              user.getId(),
              ownOsuSession.getAction(),
              ownOsuSession.getInfoText(),
              ownOsuSession.getBeatmapMd5(),
              ownOsuSession.getMods(),
              ownOsuSession.getGamemode(),
              ownOsuSession.getBeatmapId(),
              ownStats.getRankedScore(),
              ownStats.getAccuracy(),
              ownStats.getPlayCount(),
              ownStats.getTotalScore(),
              ownGlobalRank,
              ownStats.getPerformancePoints()));

      for (Session otherOsuSession : sessionService.getAllSessions()) {
        if (otherOsuSession.getId().equals(ownOsuSession.getId())) {
          continue;
        }

        int otherGlobalRank =
            Math.toIntExact(
                rankingService.getGlobalRank(
                    otherOsuSession.getGamemode(), otherOsuSession.getUser()));
        Stat otherStats =
            statService.getStats(otherOsuSession.getUser(), otherOsuSession.getGamemode());

        if (otherStats == null) {
          packetWriter.writePacket(stream, new LoginReplyPacket(-1));
          packetWriter.writePacket(stream, new AnnouncePacket("Other user's stats not found."));

          responseHeaders.add("cho-token", "no");
          return ResponseEntity.ok()
              .headers(responseHeaders)
              .contentType(MediaType.APPLICATION_OCTET_STREAM)
              .body(new ByteArrayResource(stream.toByteArray()));
        }

        // Send the other user's presence and stats to us
        packetWriter.writePacket(
            stream,
            new UserPresencePacket(
                otherOsuSession.getUser().getId(),
                otherOsuSession.getUser().getUsername(),
                otherOsuSession.getUtcOffset(),
                CountryCode.fromCode(otherOsuSession.getCountry()).getId(),
                0, // TODO: permissions
                otherOsuSession.getLatitude(),
                otherOsuSession.getLongitude(),
                otherGlobalRank));
        packetWriter.writePacket(
            stream,
            new UserStatsPacket(
                otherStats.getUser().getId(),
                otherOsuSession.getAction(),
                otherOsuSession.getInfoText(),
                otherOsuSession.getBeatmapMd5(),
                otherOsuSession.getMods(),
                otherOsuSession.getGamemode(),
                otherOsuSession.getBeatmapId(),
                otherStats.getRankedScore(),
                otherStats.getAccuracy(),
                otherStats.getPlayCount(),
                otherStats.getTotalScore(),
                otherGlobalRank,
                otherStats.getPerformancePoints()));

        // Send our presence and stats to the other user
        if (!user.isRestricted()) {
          ByteArrayOutputStream otherStream = new ByteArrayOutputStream();
          packetWriter.writePacket(
              otherStream,
              new UserPresencePacket(
                  user.getId(),
                  user.getUsername(),
                  ownOsuSession.getUtcOffset(),
                  CountryCode.fromCode(ownOsuSession.getCountry()).getId(),
                  0, // TODO: permissions
                  ownOsuSession.getLatitude(),
                  ownOsuSession.getLongitude(),
                  ownGlobalRank));
          packetWriter.writePacket(
              otherStream,
              new UserStatsPacket(
                  user.getId(),
                  ownOsuSession.getAction(),
                  ownOsuSession.getInfoText(),
                  ownOsuSession.getBeatmapMd5(),
                  ownOsuSession.getMods(),
                  ownOsuSession.getGamemode(),
                  ownOsuSession.getBeatmapId(),
                  ownStats.getRankedScore(),
                  ownStats.getAccuracy(),
                  ownStats.getPlayCount(),
                  ownStats.getTotalScore(),
                  ownGlobalRank,
                  ownStats.getPerformancePoints()));
          packetBundleService.enqueue(
              otherOsuSession.getId(), new PacketBundle(otherStream.toByteArray()));
        }
      }

      // Welcome notification
      packetWriter.writePacket(stream, new AnnouncePacket("Welcome to Banchus!"));

      // Check if the player is silenced
      if (user.getSilenceEnd() != null) {
        long secondsRemainingSilence =
            Duration.between(Instant.now(), user.getSilenceEnd()).toSeconds();
        if (secondsRemainingSilence > 0) {
          packetWriter.writePacket(
              stream, new SilenceInfoPacket(Math.toIntExact(secondsRemainingSilence)));
        } else {
          user.setSilenceEnd(null);
          user = userService.updateUser(user);
        }
      }

      // Player is restricted so notify them
      if (user.isRestricted()) {
        packetWriter.writePacket(stream, new AccountRestrictedPacket());
        packetWriter.writePacket(
            stream,
            new MessagePacket(
                "BanchoBot",
                "Your account is currently in restricted mode. Please visit the osu! website for"
                    + " more information.",
                user.getUsername(),
                1));
      }

      // TODO: Send friendlist packet

      choToken = ownOsuSession.getId().toString();

      logger.info(
          "User '{}' logged in successfully from IP {} ({})",
          loginData.getUsername(),
          ipAddress.getHostAddress(),
          geolocation.getCountryCode());
    } else {
      packetWriter.writePacket(stream, new LoginReplyPacket(-1));
      packetWriter.writePacket(stream, new AnnouncePacket("Invalid username or password."));

      choToken = "no";
    }

    responseHeaders.add("cho-token", choToken);

    return ResponseEntity.ok()
        .headers(responseHeaders)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new ByteArrayResource(stream.toByteArray()));
  }

  private ResponseEntity<Resource> handleBanchoRequest(HttpHeaders headers, byte[] data)
      throws IOException {
    Session session =
        sessionService.getSessionByID(
            UUID.fromString(Objects.requireNonNull(headers.getFirst("osu-token"))));
    ByteArrayOutputStream stream = new ByteArrayOutputStream();

    HttpHeaders responseHeaders = new HttpHeaders();

    if (session == null) {
      packetWriter.writePacket(stream, new RestartPacket(0));
      packetWriter.writePacket(stream, new AnnouncePacket("The server has restarted."));

      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(new ByteArrayResource(stream.toByteArray()));
    }

    session.setLastCommunicatedAt(Instant.now());
    sessionService.updateSession(session);

    // Read packets from request body and handle them
    List<Packet> packets = packetReader.readPackets(data);
    for (Packet packet : packets) {
      packetHandler.handlePacket(packet, session, stream);
    }

    // Dequeue all packets to send back to the client
    List<PacketBundle> ownPacketBundles = packetBundleService.dequeueAll(session.getId());
    for (PacketBundle packetBundle : ownPacketBundles) {
      stream.write(packetBundle.getData());
    }

    responseHeaders.add("cho-token", session.getId().toString());

    byte[] responseData = stream.toByteArray();
    return ResponseEntity.ok()
        .headers(responseHeaders)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(responseData.length > 0 ? new ByteArrayResource(responseData) : null);
  }
}
