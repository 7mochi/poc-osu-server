package pe.nanamochi.banchus.services;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.StatRepository;

@Service
@RequiredArgsConstructor
public class StatService {
  private final StatRepository statRepository;

  private static final float DECAY = 0.95f;

  public List<Stat> createAllGamemodes(User user) {
    // 0 = standard
    // 1 = taiko
    // 2 = catch
    // 3 = mania
    List<Stat> stats = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Stat stat = new Stat();
      stat.setUser(user);
      stat.setGamemode(Mode.fromValue(i));
      stats.add(statRepository.save(stat));
    }
    return stats;
  }

  public Stat update(Stat stat) {
    if (!statRepository.existsById(stat.getId())) {
      throw new IllegalArgumentException("Stat not found: " + stat.getId());
    }
    return statRepository.save(stat);
  }

  public Stat getStats(User user, Mode gamemode) {
    return statRepository.findByUserAndGamemode(user, gamemode);
  }

  public float calculateWeightedAccuracy(List<Score> topScores) {
    float result = 0f;
    for (int i = 0; i < topScores.size(); i++) {
      result += (float) (topScores.get(i).getAccuracy() * Math.pow(DECAY, i));
    }
    return result;
  }

  public float calculateWeightedPp(List<Score> topScores) {
    float result = 0f;
    for (int i = 0; i < topScores.size(); i++) {
      result += (float) (topScores.get(i).getPerformancePoints() * Math.pow(DECAY, i));
    }
    return result;
  }
}
