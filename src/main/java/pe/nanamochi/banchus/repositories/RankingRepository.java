package pe.nanamochi.banchus.repositories;

import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;

@Repository
@AllArgsConstructor
public class RankingRepository {
  private final RedisTemplate<String, String> redisTemplate;

  public long getGlobalRank(Mode mode, User user) {
    if (user.isRestricted()) {
      return 0;
    }
    Long rank =
        redisTemplate.opsForZSet().reverseRank(makeKeyGlobal(mode), String.valueOf(user.getId()));

    return rank != null ? rank + 1 : 0;
  }

  public long getCountryRank(Mode mode, User user, CountryCode countryCode) {
    if (user.isRestricted()) {
      return 0;
    }
    Long rank =
        redisTemplate
            .opsForZSet()
            .reverseRank(makeKeyCountry(mode, countryCode), String.valueOf(user.getId()));

    return rank != null ? rank + 1 : 0;
  }

  public long updateRanking(Mode mode, User user, Stat stat) {
    if (user.isRestricted()) {
      return 0;
    }

    redisTemplate
        .opsForZSet()
        .add(makeKeyGlobal(mode), String.valueOf(user.getId()), stat.getPerformancePoints());
    redisTemplate
        .opsForZSet()
        .add(
            makeKeyCountry(mode, user.getCountry()),
            String.valueOf(user.getId()),
            stat.getPerformancePoints());

    return getGlobalRank(mode, user);
  }

  public String makeKeyGlobal(Mode mode) {
    return "server:ranking:" + mode.getValue();
  }

  public String makeKeyCountry(Mode mode, CountryCode countryCode) {
    return "server:ranking:" + mode.getValue() + ":" + countryCode.getCode();
  }
}
