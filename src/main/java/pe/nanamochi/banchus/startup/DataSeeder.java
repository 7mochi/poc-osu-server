package pe.nanamochi.banchus.startup;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.db.Channel;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.ChannelRepository;
import pe.nanamochi.banchus.repositories.UserRepository;
import pe.nanamochi.banchus.services.StatService;

@Component
public class DataSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final ChannelRepository channelRepository;
  private final StatService statService;

  public DataSeeder(
      UserRepository userRepository, ChannelRepository channelRepository, StatService statService) {
    this.userRepository = userRepository;
    this.channelRepository = channelRepository;
    this.statService = statService;
  }

  @Override
  public void run(String... args) {
    seedUsers();
    seedChannels();
  }

  private void seedUsers() {
    if (userRepository.count() > 0) return;

    User user = new User();
    user.setUsername("test");
    user.setEmail("test@gmail.com");
    user.setPasswordMd5("098f6bcd4621d373cade4e832627b4f6"); // test
    user.setCountry(CountryCode.KP);
    user.setPrivileges(0);
    user.setRestricted(false);

    userRepository.save(user);
    statService.createAllGamemodes(user);

    User user2 = new User();
    user2.setUsername("test2");
    user2.setEmail("test2@gmail.com");
    user2.setPasswordMd5("098f6bcd4621d373cade4e832627b4f6"); // test
    user2.setCountry(CountryCode.AR);
    user2.setPrivileges(0);
    user2.setRestricted(true);

    userRepository.save(user2);
    statService.createAllGamemodes(user2);
  }

  private void seedChannels() {
    if (channelRepository.count() > 0) return;

    List<Channel> channels = new ArrayList<>(7);

    Channel osuChannel = new Channel();
    osuChannel.setName("#osu");
    osuChannel.setTopic("General discussion.");
    osuChannel.setReadPrivileges(1);
    osuChannel.setWritePrivileges(2);
    osuChannel.setAutoJoin(true);
    channels.add(osuChannel);

    Channel announceChannel = new Channel();
    announceChannel.setName("#announce");
    announceChannel.setTopic("Exemplary performance and public announcements.");
    announceChannel.setReadPrivileges(1);
    announceChannel.setWritePrivileges(24576);
    announceChannel.setAutoJoin(true);
    channels.add(announceChannel);

    Channel lobbyChannel = new Channel();
    lobbyChannel.setName("#lobby");
    lobbyChannel.setTopic("Multiplayer lobby discussion room.");
    lobbyChannel.setReadPrivileges(1);
    lobbyChannel.setWritePrivileges(2);
    lobbyChannel.setAutoJoin(false);
    channels.add(lobbyChannel);

    Channel supporterChannel = new Channel();
    supporterChannel.setName("#supporter");
    supporterChannel.setTopic("General discussion for supporters.");
    supporterChannel.setReadPrivileges(48);
    supporterChannel.setWritePrivileges(48);
    supporterChannel.setAutoJoin(false);
    channels.add(supporterChannel);

    Channel staffChannel = new Channel();
    staffChannel.setName("#staff");
    staffChannel.setTopic("General discussion for staff members.");
    staffChannel.setReadPrivileges(28672);
    staffChannel.setWritePrivileges(28672);
    staffChannel.setAutoJoin(true);
    channels.add(staffChannel);

    Channel adminChannel = new Channel();
    adminChannel.setName("#admin");
    adminChannel.setTopic("General discussion for administrators.");
    adminChannel.setReadPrivileges(24576);
    adminChannel.setWritePrivileges(24576);
    adminChannel.setAutoJoin(true);
    channels.add(adminChannel);

    Channel devChannel = new Channel();
    devChannel.setName("#dev");
    devChannel.setTopic("General discussion for developers.");
    devChannel.setReadPrivileges(16384);
    devChannel.setWritePrivileges(16384);
    devChannel.setAutoJoin(true);
    channels.add(devChannel);

    channelRepository.saveAll(channels);
  }
}
