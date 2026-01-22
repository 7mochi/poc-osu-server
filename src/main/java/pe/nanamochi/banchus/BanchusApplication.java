package pe.nanamochi.banchus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import pe.nanamochi.banchus.startup.RedisContextInitializer;

@SpringBootApplication
@EnableJpaAuditing
public class BanchusApplication {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(BanchusApplication.class);
    app.addInitializers(new RedisContextInitializer());
    app.run(args);
  }
}
