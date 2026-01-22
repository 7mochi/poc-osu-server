package pe.nanamochi.banchus.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

public class RedisContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger logger = LoggerFactory.getLogger(RedisContextInitializer.class);

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    String host = context.getEnvironment().getProperty("spring.data.redis.host", "localhost");
    int port =
        Integer.parseInt(context.getEnvironment().getProperty("spring.data.redis.port", "6379"));

    LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
    factory.afterPropertiesSet();

    try (var connection = factory.getConnection()) {
      connection.ping();
      logger.info("Successfully connected to Redis server.");
    }
  }
}
