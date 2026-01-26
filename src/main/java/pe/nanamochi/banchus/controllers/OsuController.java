package pe.nanamochi.banchus.controllers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
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
import pe.nanamochi.banchus.services.ChannelMembersRedisService;
import pe.nanamochi.banchus.services.ChannelService;
import pe.nanamochi.banchus.services.PacketBundleService;
import pe.nanamochi.banchus.services.SessionService;
import pe.nanamochi.banchus.services.StatService;
import pe.nanamochi.banchus.services.UserService;
import pe.nanamochi.banchus.utils.IPApi;
import pe.nanamochi.banchus.utils.Security;
import pe.nanamochi.banchus.utils.Validation;

@RestController
@RequestMapping("/bancho")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class OsuController {

  private static final Logger logger = LoggerFactory.getLogger(OsuController.class);

  @Autowired private PacketWriter packetWriter;

  @Autowired private PacketReader packetReader;

  @Autowired private PacketHandler packetHandler;

  @Autowired private UserService userService;

  @Autowired private SessionService sessionService;

  @Autowired private StatService statService;

  @Autowired private ChannelService channelService;

  @Autowired private PacketBundleService packetBundleService;

  @Autowired private ChannelMembersRedisService channelMembersRedisService;

  @PostMapping(value = "/", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<Resource> banchoHandler(
      @RequestHeader HttpHeaders headers, @RequestBody byte[] data) throws IOException {
    ResponseEntity<Resource> response = null;

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
    InetAddress ipAddress = null;

    HttpHeaders responseHeaders = new HttpHeaders();
    String choToken = "";

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
    Geolocation geolocation = null;

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

      // TODO: Write privileges packet
      if (!PrivilegesUtil.has(user.getPrivileges(), Privileges.VERIFIED)) {
        user.setPrivileges(PrivilegesUtil.add(user.getPrivileges(), Privileges.VERIFIED));
        userService.updateUser(user);

        packetWriter.writePacket(stream, new LoginPermissionsPacket(user.getPrivileges()));
      }

      // TODO: Write osu chat channels packet

      // Autojoin to channels
      List<Channel> autoJoinChannels = channelService.findByAutoJoinTrue();
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
              CountryCode.fromCode(ownOsuSession.getCountry()).id(),
              0, // TODO: permissions
              ownOsuSession.getLatitude(),
              ownOsuSession.getLongitude(),
              727 // TODO: global rank
              ));
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
              727, // TODO: global rank
              ownStats.getPerformancePoints()));

      for (Session otherOsuSession : sessionService.getAllSessions()) {
        if (otherOsuSession.getId().equals(ownOsuSession.getId())) {
          continue;
        }

        // TODO: Get other session's global rank

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
                CountryCode.fromCode(otherOsuSession.getCountry()).id(),
                0, // TODO: permissions
                otherOsuSession.getLatitude(),
                otherOsuSession.getLongitude(),
                727 // TODO: global rank
                ));
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
                727, // TODO: global rank
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
                  CountryCode.fromCode(ownOsuSession.getCountry()).id(),
                  0, // TODO: permissions
                  ownOsuSession.getLatitude(),
                  ownOsuSession.getLongitude(),
                  727 // TODO: global rank
                  ));
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
                  727, // TODO: global rank
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

  @PostMapping(value = "/users")
  public ResponseEntity<String> registerAccount(
      @RequestHeader MultiValueMap<String, String> headers,
      @RequestParam MultiValueMap<String, String> paramMap)
      throws NoSuchAlgorithmException, UnknownHostException {
    if (!paramMap.containsKey("user[username]")
        || !paramMap.containsKey("user[user_email]")
        || !paramMap.containsKey("user[password]")) {
      return ResponseEntity.badRequest().body("Missing required params");
    }

    String username = paramMap.get("user[username]").getFirst();
    String email = paramMap.get("user[user_email]").getFirst();
    String passwordPlainText = paramMap.get("user[password]").getFirst();
    int check = Integer.parseInt(paramMap.get("check").getFirst());

    if (check == 0) {
      HashMap<String, String> errors = new HashMap<>();
      if (!Validation.isValidUsername(username)) {
        errors.put("username", "Invalid username.");
      }
      if (userService.findByUsername(username) != null) {
        errors.put("username", "Username already taken by another player.");
      }
      if (!Validation.isValidEmail(email)) {
        errors.put("user_email", "Invalid email syntax.");
      }
      if (userService.findByEmail(email) != null) {
        errors.put("user_email", "Email already taken by another player.");
      }
      if (!Validation.isValidPassword(passwordPlainText)) {
        errors.put(
            "password",
            "Password must be between 8 and 32 characters and contain more than 3 unique"
                + " characters.");
      }

      if (!errors.isEmpty()) {
        StringBuilder responseBody = new StringBuilder("{\"form_error\": {\"user\": {");
        int count = 0;
        for (String field : errors.keySet()) {
          if (count > 0) {
            responseBody.append(", ");
          }
          responseBody
              .append("\"")
              .append(field)
              .append("\": [\"")
              .append(errors.get(field))
              .append("\"]");
          count++;
        }
        responseBody.append("}}}");
        return ResponseEntity.badRequest().body(responseBody.toString());
      }

      User user = new User();
      user.setUsername(username);
      user.setEmail(email);
      user.setPasswordMd5(Security.getMd5(passwordPlainText));
      user.setCountry(CountryCode.KP); // Default to North Korea for now
      user.setRestricted(false);
      user = userService.createUser(user);
      statService.createAllGamemodes(user);
    }

    return ResponseEntity.ok("ok");
  }
}
