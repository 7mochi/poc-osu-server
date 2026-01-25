package pe.nanamochi.banchus.repositories;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class ChannelMembersRedisRepository {

    private static final String KEY_PREFIX = "server:channel-members:";

    private final StringRedisTemplate redis;

    public ChannelMembersRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String makeKey(UUID channelId) {
        return KEY_PREFIX + channelId.toString();
    }

    private String serialize(UUID sessionId) {
        return sessionId.toString();
    }

    private UUID deserialize(String value) {
        return UUID.fromString(value);
    }

    public UUID add(UUID channelId, UUID sessionId) {
        redis.opsForSet().add(makeKey(channelId), serialize(sessionId));
        return sessionId;
    }

    public UUID remove(UUID channelId, UUID sessionId) {
        Long removed = redis.opsForSet().remove(makeKey(channelId), serialize(sessionId));
        return (removed != null && removed == 1L) ? sessionId : null;
    }

    public Set<UUID> members(UUID channelId) {
        Set<String> raw = redis.opsForSet().members(makeKey(channelId));
        if (raw.isEmpty()) {
            return Set.of();
        }
        return raw.stream()
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }
}
