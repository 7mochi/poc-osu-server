package pe.nanamochi.banchus.services;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.StatRepository;

@Service
public class StatService {

  @Autowired private StatRepository statRepository;

  public List<Stat> createAllGamemodes(User user) {
    // 0 = standard
    // 1 = taiko
    // 2 = catch
    // 3 = mania
    List<Stat> stats = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Stat stat = new Stat();
      stat.setUser(user);
      stat.setGamemode(i);
      stats.add(statRepository.save(stat));
    }
    return stats;
  }

  public Stat getStats(User user, int gamemode) {
    return statRepository.findByUserAndGamemode(user, gamemode);
  }
}
