package pe.nanamochi.poc_osu_server.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.nanamochi.poc_osu_server.entities.db.Stat;
import pe.nanamochi.poc_osu_server.entities.db.User;

@Repository
public interface StatRepository extends JpaRepository<Stat, Integer> {
    Stat findByUserAndGamemode(User user, Integer gamemode);
}
