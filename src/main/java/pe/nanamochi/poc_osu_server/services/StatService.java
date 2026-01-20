package pe.nanamochi.poc_osu_server.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.poc_osu_server.entities.db.Stat;
import pe.nanamochi.poc_osu_server.entities.db.User;
import pe.nanamochi.poc_osu_server.repositories.StatRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatService {

    @Autowired
    private StatRepository statRepository;

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

