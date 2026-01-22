package pe.nanamochi.banchus.repositories;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.PacketBundle;

@Repository
public class PacketBundleRepository {
  private final RedisTemplate<String, PacketBundle> redisTemplate;
  private static final Logger logger = LoggerFactory.getLogger(PacketBundleRepository.class);

  public PacketBundleRepository(RedisTemplate<String, PacketBundle> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void enqueue(UUID sessionId, PacketBundle packetBundle) {
    Long queueSize = redisTemplate.opsForList().rightPush(makeKey(sessionId), packetBundle);

    if (queueSize > 50) {
      logger.warn("Packet bundle size exceeded 50 items for Session ID: {}", sessionId);
    }
  }

  public PacketBundle dequeueOne(UUID sessionId) {
    return redisTemplate.opsForList().leftPop(makeKey(sessionId));
  }

  public List<PacketBundle> dequeueAll(UUID sessionId) {
    List<PacketBundle> bundles = redisTemplate.opsForList().range(makeKey(sessionId), 0, -1);
    if (!bundles.isEmpty()) {
      redisTemplate.delete(makeKey(sessionId));
    }
    return bundles;
  }

  private String makeKey(UUID sessionId) {
    return "server:packet-bundles:" + sessionId;
  }
}
