package pe.nanamochi.poc_osu_server.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import pe.nanamochi.poc_osu_server.entities.Geolocation;
import pe.nanamochi.poc_osu_server.entities.LoginData;
import pe.nanamochi.poc_osu_server.entities.db.Session;
import pe.nanamochi.poc_osu_server.entities.db.Stat;
import pe.nanamochi.poc_osu_server.entities.db.User;
import pe.nanamochi.poc_osu_server.services.SessionService;
import pe.nanamochi.poc_osu_server.services.StatService;
import pe.nanamochi.poc_osu_server.services.UserService;
import pe.nanamochi.poc_osu_server.utils.IPApi;
import pe.nanamochi.poc_osu_server.utils.Security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/bancho")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class OsuController {

    @Autowired
    private UserService userService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private StatService statService;

    @PostMapping(value = "/", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> banchoHandler(@RequestHeader MultiValueMap<String, String> headers, @RequestBody String data) throws IOException {
        System.out.println("Received bancho request");

        ResponseEntity<Resource> response = null;

        if (!headers.containsKey("osu-token")) {
            response = handleLogin(headers, data);
        } else {
            response = handleBanchoRequest(headers, data);
        }

        return response;
    }

    private ResponseEntity<Resource> handleLogin(MultiValueMap<String, String> headers, String data) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        LoginData loginData = new LoginData(data);
        InetAddress ipAddress = null;

        HttpHeaders responseHeaders = new HttpHeaders();
        String choToken = "";

        if (!headers.containsKey("X-Real-IP")) {
            User user = new User(); // Temporary user to write the failure packets
            user.writeLoginReply(stream, -1);
            user.writeAnnouncement(stream, "Could not determine your IP address.");

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
        } else {
            geolocation = IPApi.fetchFromIP(ipAddress);
        }

        if (user != null) {
            Session session = new Session();
            session.setUser(user);
            session.setUtcOffset(Integer.parseInt(loginData.getUtcOffset()));
            session.setGamemode(0);
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

            user.writeProtocolNegotiation(stream);
            user.writeLoginReply(stream, user.getId());
            user.writeAnnouncement(stream, "Welcome to this Poc Osu! Server!");
            user.writeUserPresence(stream, session);
            user.writeUserStats(stream, session, ownStats);
            user.writeChannelInfoComplete(stream);

            choToken = session.getId().toString();
        } else {
            user = new User(); // Temporary user to write the failure packets
            user.writeLoginReply(stream, -1);
            user.writeAnnouncement(stream, "Invalid username or password.");

            choToken = "no";
        }

        responseHeaders.add("cho-token", choToken);

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(stream.toByteArray()));
    }

    private ResponseEntity<Resource> handleBanchoRequest(MultiValueMap<String, String> headers, String data) throws IOException {
        Session session = sessionService.getSessionByID(UUID.fromString(Objects.requireNonNull(headers.getFirst("osu-token"))));
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        if (session == null) {
            User user = new User(); // Temporary user to write the failure packets
            user.writeRestart(stream, 0);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(stream.toByteArray()));
        }

        return null;
    }

    @PostMapping(value = "/users")
    public ResponseEntity<String> registerAccount(@RequestHeader MultiValueMap<String, String> headers, @RequestParam MultiValueMap<String,String> paramMap) throws NoSuchAlgorithmException, UnknownHostException {
        // TODO: Add check to see if username, email, and password are present

        String username = paramMap.get("user[username]").getFirst();
        String email = paramMap.get("user[user_email]").getFirst();
        String passwordPlainText = paramMap.get("user[password]").getFirst();
        String passwordMd5 = Security.getMd5(passwordPlainText);
        int check = Integer.parseInt(paramMap.get("check").getFirst());

        System.out.println(username);
        System.out.println(email);
        System.out.println(passwordPlainText);
        System.out.println(passwordMd5);
        System.out.println(check);

        // TODO: Add checks for username, email, and password
        // Usernames must:
        // - be between 2 and 15 characters in length
        // - not contain both ' ' and '_', one is fine
        // - not be in the config's `disallowed_names` list
        // - not already be taken by another player
        // Emails must:
        // - match the regex `^[^@\s]{1,200}@[^@\s\.]{1,30}\.[^@\.\s]{1,24}$`
        // - not already be taken by another player
        // Passwords must:
        // - be within 8-32 characters in length
        // - have more than 3 unique characters
        // - not be in the config's `disallowed_passwords` list

        if (check == 0) {
            User user = userService.createUser(username, passwordPlainText, email, 0);
            statService.createAllGamemodes(user);
        }

        return ResponseEntity.ok("ok");
    }
}
