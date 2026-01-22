package pe.nanamochi.banchus.startup;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.UserRepository;
import pe.nanamochi.banchus.services.StatService;

@Component
public class DataSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final StatService statService;

  public DataSeeder(UserRepository userRepository, StatService statService) {
    this.userRepository = userRepository;
    this.statService = statService;
  }

  @Override
  public void run(String... args) {
    if (userRepository.count() == 0) {
      User user = new User();
      user.setUsername("test");
      user.setEmail("test@gmail.com");
      user.setPasswordMd5("098f6bcd4621d373cade4e832627b4f6"); // test
      user.setCountry(169); // 169 -> PE

      userRepository.save(user);
      statService.createAllGamemodes(user);

      User user2 = new User();
      user2.setUsername("test2");
      user2.setEmail("test2@gmail.com");
      user2.setPasswordMd5("098f6bcd4621d373cade4e832627b4f6"); // test
      user2.setCountry(169); // 169 -> PE

      userRepository.save(user2);
      statService.createAllGamemodes(user2);
    }
  }
}
