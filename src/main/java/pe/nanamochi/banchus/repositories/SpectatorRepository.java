package pe.nanamochi.banchus.repositories;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SpectatorRepository {
  private final RedisTemplate<String, UUID> redisTemplate;

  public UUID add(UUID hostSessionId, UUID sessionId) {
    redisTemplate.opsForSet().add(makeKey(hostSessionId), sessionId);
    return sessionId;
  }

  public UUID remove(UUID hostSessionId, UUID sessionId) {
    Long removed = redisTemplate.opsForSet().remove(makeKey(hostSessionId), sessionId);
    return (removed != null && removed > 0) ? sessionId : null;
  }

  public Set<UUID> getMembers(UUID hostSessionId) {
    Set<UUID> raw = redisTemplate.opsForSet().members(makeKey(hostSessionId));
    if (raw == null || raw.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(raw);
  }

  private String makeKey(UUID hostSessionId) {
    return "server:spectators:" + hostSessionId;
  }
}
