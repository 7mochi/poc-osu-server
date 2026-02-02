package pe.nanamochi.banchus.repositories;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelMembersRepository {
  private final RedisTemplate<String, String> redisTemplate;

  public ChannelMembersRepository(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public UUID add(UUID channelId, UUID sessionId) {
    redisTemplate.opsForSet().add(makeKey(channelId), sessionId.toString());
    return sessionId;
  }

  public UUID remove(UUID channelId, UUID sessionId) {
    Long removed = redisTemplate.opsForSet().remove(makeKey(channelId), sessionId.toString());
    return (removed != null && removed == 1L) ? sessionId : null;
  }

  public Set<UUID> getMembers(UUID channelId) {
    Set<String> raw = redisTemplate.opsForSet().members(makeKey(channelId));
    if (raw.isEmpty()) {
      return Set.of();
    }
    return raw.stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
  }

  private String makeKey(UUID channelId) {
    return "server:channel-members:" + channelId.toString();
  }
}
