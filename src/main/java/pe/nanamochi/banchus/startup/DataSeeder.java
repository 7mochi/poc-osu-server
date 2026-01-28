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
    user.setRestricted(false);

    userRepository.save(user);
    statService.createAllGamemodes(user);

    User user2 = new User();
    user2.setUsername("test2");
    user2.setEmail("test2@gmail.com");
    user2.setPasswordMd5("098f6bcd4621d373cade4e832627b4f6"); // test
    user2.setCountry(CountryCode.AR);
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
    osuChannel.setReadPrivileges(0);
    osuChannel.setWritePrivileges(0);
    osuChannel.setAutoJoin(true);
    channels.add(osuChannel);

    Channel lobbyChannel = new Channel();
    lobbyChannel.setName("#lobby");
    lobbyChannel.setTopic("General multiplayer lobby chat.");
    lobbyChannel.setReadPrivileges(0);
    lobbyChannel.setWritePrivileges(1 << 0);
    lobbyChannel.setAutoJoin(true);
    channels.add(lobbyChannel);

    Channel announceChannel = new Channel();
    announceChannel.setName("#announce");
    announceChannel.setTopic("Announcements from the server.");
    announceChannel.setReadPrivileges(1 << 0);
    announceChannel.setWritePrivileges(1 << 9);
    announceChannel.setAutoJoin(true);
    channels.add(announceChannel);

    Channel supporterChannel = new Channel();
    supporterChannel.setName("#help");
    supporterChannel.setTopic("Help and support.");
    supporterChannel.setReadPrivileges(1 << 0);
    supporterChannel.setWritePrivileges(1 << 0);
    supporterChannel.setAutoJoin(true);
    channels.add(supporterChannel);

    Channel staffChannel = new Channel();
    staffChannel.setName("#staff");
    staffChannel.setTopic("General discussion for staff members.");
    staffChannel.setReadPrivileges(1 << 7 | 1 << 9 | 1 << 13 | 1 << 30);
    staffChannel.setWritePrivileges(1 << 7 | 1 << 9 | 1 << 13 | 1 << 30);
    staffChannel.setAutoJoin(true);
    channels.add(staffChannel);

    Channel devChannel = new Channel();
    devChannel.setName("#dev");
    devChannel.setTopic("General discussion for developers.");
    devChannel.setReadPrivileges(1 << 30);
    devChannel.setWritePrivileges(1 << 30);
    devChannel.setAutoJoin(true);
    channels.add(devChannel);

    channelRepository.saveAll(channels);
  }
}
