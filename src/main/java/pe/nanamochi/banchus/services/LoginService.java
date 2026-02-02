package pe.nanamochi.banchus.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.*;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.packets.PacketWriter;
import pe.nanamochi.banchus.packets.server.*;
import pe.nanamochi.banchus.utils.PrivilegesUtil;

@Service
@RequiredArgsConstructor
public class LoginService {

  private static final Logger logger = LoggerFactory.getLogger(LoginService.class);

  private final UserService userService;
  private final SessionService sessionService;
  private final StatService statService;
  private final ChannelService channelService;
  private final ChannelMembersRedisService channelMembersRedisService;
  private final PacketBundleService packetBundleService;
  private final RankingService rankingService;
  private final IPApiService ipApiService;
  private final PacketWriter packetWriter;

  public String handleLogin(LoginData loginData, HttpHeaders headers, ByteArrayOutputStream stream)
      throws IOException {

    if (!headers.containsHeader("X-Real-IP")) {
      packetWriter.writePacket(stream, new LoginReplyPacket(-1));
      packetWriter.writePacket(stream, new AnnouncePacket("Could not determine your IP address."));
      return "no";
    }

    InetAddress ipAddress = InetAddress.getByName(headers.getFirst("X-Real-IP"));
    User user = userService.login(loginData.getUsername(), loginData.getPasswordMd5());
    Geolocation geolocation;

    if (ipAddress.isAnyLocalAddress() || ipAddress.isLoopbackAddress()) {
      geolocation = new Geolocation();
      geolocation.setLat(0.0f);
      geolocation.setLon(0.0f);
      geolocation.setCountryCode("kp");
    } else {
      geolocation = ipApiService.fetchFromIP(ipAddress);
    }

    if (user == null) {
      packetWriter.writePacket(stream, new LoginReplyPacket(-1));
      packetWriter.writePacket(stream, new AnnouncePacket("Invalid username or password."));
      return "no";
    }

    Session ownOsuSession = createSession(loginData, user, geolocation);
    Stat ownStats = statService.getStats(user, ownOsuSession.getGamemode());
    if (ownStats == null) {
      packetWriter.writePacket(stream, new LoginReplyPacket(-1));
      packetWriter.writePacket(stream, new AnnouncePacket("Own stats not found."));
      return "no";
    }

    int ownGlobalRank =
        Math.toIntExact(rankingService.getGlobalRank(ownOsuSession.getGamemode(), user));

    writeLoginSuccessPackets(stream, user, ownOsuSession, ownStats, ownGlobalRank);
    sendPresenceToOtherUsers(ownOsuSession, ownStats, ownGlobalRank);
    sendOtherUsersPresenceToSelf(stream, ownOsuSession);
    sendWelcomeAndStatusPackets(stream, user);

    logger.info(
        "User '{}' logged in successfully from IP {} ({})",
        loginData.getUsername(),
        ipAddress.getHostAddress(),
        geolocation.getCountryCode());

    return ownOsuSession.getId().toString();
  }

  private Session createSession(LoginData loginData, User user, Geolocation geolocation) {
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
    return sessionService.saveSession(ownOsuSession);
  }

  private void writeLoginSuccessPackets(
      ByteArrayOutputStream stream, User user, Session session, Stat stats, int globalRank)
      throws IOException {
    packetWriter.writePacket(stream, new ProtocolNegotiationPacket());
    packetWriter.writePacket(stream, new LoginReplyPacket(user.getId()));
    packetWriter.writePacket(
        stream,
        new LoginPermissionsPacket(
            PrivilegesUtil.serverToClientPrivileges(
                user.getPrivileges() | ServerPrivileges.SUPPORTER.getValue())));

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
    packetWriter.writePacket(stream, new ChannelInfoCompletePacket());

    packetWriter.writePacket(
        stream,
        new UserPresencePacket(
            user.getId(),
            user.getUsername(),
            session.getUtcOffset(),
            CountryCode.fromCode(session.getCountry()).getId(),
            0, // TODO: permissions
            session.getLatitude(),
            session.getLongitude(),
            globalRank));
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
            stats.getRankedScore(),
            stats.getAccuracy(),
            stats.getPlayCount(),
            stats.getTotalScore(),
            globalRank,
            stats.getPerformancePoints()));
  }

  private void sendPresenceToOtherUsers(Session ownSession, Stat ownStats, int ownGlobalRank)
      throws IOException {
    if (ownSession.getUser().isRestricted()) {
      return;
    }

    ByteArrayOutputStream otherStream = new ByteArrayOutputStream();
    packetWriter.writePacket(
        otherStream,
        new UserPresencePacket(
            ownSession.getUser().getId(),
            ownSession.getUser().getUsername(),
            ownSession.getUtcOffset(),
            CountryCode.fromCode(ownSession.getCountry()).getId(),
            0, // TODO: permissions
            ownSession.getLatitude(),
            ownSession.getLongitude(),
            ownGlobalRank));
    packetWriter.writePacket(
        otherStream,
        new UserStatsPacket(
            ownSession.getUser().getId(),
            ownSession.getAction(),
            ownSession.getInfoText(),
            ownSession.getBeatmapMd5(),
            ownSession.getMods(),
            ownSession.getGamemode(),
            ownSession.getBeatmapId(),
            ownStats.getRankedScore(),
            ownStats.getAccuracy(),
            ownStats.getPlayCount(),
            ownStats.getTotalScore(),
            ownGlobalRank,
            ownStats.getPerformancePoints()));
    PacketBundle packetBundle = new PacketBundle(otherStream.toByteArray());

    for (Session otherSession : sessionService.getAllSessions()) {
      if (!otherSession.getId().equals(ownSession.getId())) {
        packetBundleService.enqueue(otherSession.getId(), packetBundle);
      }
    }
  }

  private void sendOtherUsersPresenceToSelf(ByteArrayOutputStream stream, Session ownSession)
      throws IOException {
    for (Session otherSession : sessionService.getAllSessions()) {
      if (otherSession.getId().equals(ownSession.getId())) {
        continue;
      }

      Stat otherStats = statService.getStats(otherSession.getUser(), otherSession.getGamemode());
      if (otherStats == null) {
        packetWriter.writePacket(stream, new LoginReplyPacket(-1));
        packetWriter.writePacket(stream, new AnnouncePacket("Other user's stats not found."));
      }

      int otherGlobalRank =
          Math.toIntExact(
              rankingService.getGlobalRank(otherSession.getGamemode(), otherSession.getUser()));

      packetWriter.writePacket(
          stream,
          new UserPresencePacket(
              otherSession.getUser().getId(),
              otherSession.getUser().getUsername(),
              otherSession.getUtcOffset(),
              CountryCode.fromCode(otherSession.getCountry()).getId(),
              0, // TODO: permissions
              otherSession.getLatitude(),
              otherSession.getLongitude(),
              otherGlobalRank));
      packetWriter.writePacket(
          stream,
          new UserStatsPacket(
              otherStats.getUser().getId(),
              otherSession.getAction(),
              otherSession.getInfoText(),
              otherSession.getBeatmapMd5(),
              otherSession.getMods(),
              otherSession.getGamemode(),
              otherSession.getBeatmapId(),
              otherStats.getRankedScore(),
              otherStats.getAccuracy(),
              otherStats.getPlayCount(),
              otherStats.getTotalScore(),
              otherGlobalRank,
              otherStats.getPerformancePoints()));
    }
  }

  private void sendWelcomeAndStatusPackets(ByteArrayOutputStream stream, User user)
      throws IOException {
    packetWriter.writePacket(stream, new AnnouncePacket("Welcome to Banchus!"));

    if (user.getSilenceEnd() != null) {
      long secondsRemainingSilence =
          Duration.between(Instant.now(), user.getSilenceEnd()).toSeconds();
      if (secondsRemainingSilence > 0) {
        packetWriter.writePacket(
            stream, new SilenceInfoPacket(Math.toIntExact(secondsRemainingSilence)));
      } else {
        user.setSilenceEnd(null);
        userService.updateUser(user);
      }
    }

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
  }
}
