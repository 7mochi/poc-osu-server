package pe.nanamochi.banchus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import pe.nanamochi.banchus.entities.PacketBundle;

@Configuration
public class RedisConfig {

  @Bean
  public RedisTemplate<String, PacketBundle> packetBundleRedisTemplate(
      RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, PacketBundle> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(RedisSerializer.json());

    return template;
  }
}
