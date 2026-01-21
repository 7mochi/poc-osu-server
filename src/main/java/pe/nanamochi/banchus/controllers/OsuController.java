package pe.nanamochi.banchus.controllers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.Geolocation;
import pe.nanamochi.banchus.entities.LoginData;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.PacketHandler;
import pe.nanamochi.banchus.packets.PacketReader;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.services.SessionService;
import pe.nanamochi.banchus.services.StatService;
import pe.nanamochi.banchus.services.UserService;
import pe.nanamochi.banchus.utils.IPApi;
import pe.nanamochi.banchus.utils.Validation;

@RestController
@RequestMapping("/bancho")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class OsuController {

  @Autowired private PacketWriter packetWriter;

  @Autowired private PacketReader packetReader;

  @Autowired private PacketHandler packetHandler;

  @Autowired private UserService userService;

  @Autowired private SessionService sessionService;

  @Autowired private StatService statService;

  @PostMapping(value = "/", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<Resource> banchoHandler(
      @RequestHeader MultiValueMap<String, String> headers, @RequestBody byte[] data)
      throws IOException {
    System.out.println("Received bancho request");

    ResponseEntity<Resource> response = null;

    if (!headers.containsKey("osu-token")) {
      response = handleLogin(headers, new String(data, StandardCharsets.UTF_8));
    } else {
      response = handleBanchoRequest(headers, data);
    }

    return response;
  }

  private ResponseEntity<Resource> handleLogin(MultiValueMap<String, String> headers, String data)
      throws IOException {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    LoginData loginData = new LoginData(data);
    InetAddress ipAddress = null;

    HttpHeaders responseHeaders = new HttpHeaders();
    String choToken = "";

    if (!headers.containsKey("X-Real-IP")) {
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
      Session session = new Session();
      session.setUser(user);
      session.setUtcOffset(Integer.parseInt(loginData.getUtcOffset()));
      session.setGamemode(0);
      session.setCountry(geolocation.getCountryCode().toLowerCase());
      session.setLatitude(geolocation.getLat());
      session.setLongitude(geolocation.getLon());
      session.setDisplayCityLocation(Boolean.parseBoolean(loginData.getDisplayCity()));
      session.setAction(0);
      session.setInfoText("");
      session.setBeatmapMd5("");
      session.setBeatmapId(0);
      session.setMods(0); // TODO: implement mods enum
      session.setPmPrivate(Boolean.parseBoolean(loginData.getPmPrivate()));
      session.setReceiveMatchUpdates(false);
      session.setSpectatorHostSessionId(null);
      session.setAwayMessage("");
      session.setMultiplayerMatchId(0); // TODO: idk if this is the correct value
      session.setLastCommunicatedAt(Instant.now());
      session.setLastNpBeatmapId(0); // TODO: Idk if this is the correct value
      session.setPrimarySession(true); // TODO: support multiple sessions for tournament client
      session.setOsuVersion(loginData.getOsuVersion());
      session.setOsuPathMd5(loginData.getOsuPathMd5());
      session.setAdaptersStr(loginData.getAdaptersStr());
      session.setAdaptersMd5(loginData.getAdaptersMd5());
      session.setUninstallMd5(loginData.getUninstallMd5());
      session.setDiskSignatureMd5(loginData.getDiskSignatureMd5());
      session = sessionService.saveSession(session);

      Stat ownStats = statService.getStats(user, 0); // Standard mode stats

      packetWriter.writePacket(stream, new ProtocolNegotiationPacket());
      packetWriter.writePacket(stream, new LoginReplyPacket(user.getId()));
      packetWriter.writePacket(stream, new AnnouncePacket("Welcome to Banchus!"));
      packetWriter.writePacket(
          stream,
          new UserPresencePacket(
              user.getId(),
              user.getUsername(),
              session.getUtcOffset(),
              CountryCode.fromCode(session.getCountry()).id(),
              0, // TODO: permissions
              session.getLatitude(),
              session.getLongitude(),
              727 // TODO: global rank
              ));
      packetWriter.writePacket(
          stream,
          new UserStatsPacket(
              user.getId(),
              session.getAction(),
              session.getInfoText(),
              session.getBeatmapMd5(),
              session.getMods(),
              session.getGamemode(),
              session.getBeatmapId(),
              ownStats.getRankedScore(),
              ownStats.getAccuracy(),
              ownStats.getPlayCount(),
              ownStats.getTotalScore(),
              727, // TODO: global rank
              ownStats.getPerformancePoints()));
      packetWriter.writePacket(stream, new ChannelInfoCompletePacket());

      choToken = session.getId().toString();
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

  private ResponseEntity<Resource> handleBanchoRequest(
      MultiValueMap<String, String> headers, byte[] data) throws IOException {
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

    // Read packets from request body
    List<Packet> packets = packetReader.readPackets(data);
    for (Packet packet : packets) {
      System.out.println("Received packet: " + packet.getPacketType());
      packetHandler.handlePacket(packet, session, stream);
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

      User user = userService.createUser(username, passwordPlainText, email, 0);
      statService.createAllGamemodes(user);
    }

    return ResponseEntity.ok("ok");
  }
}
