package pe.nanamochi.banchus.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;

@Repository
public interface StatRepository extends JpaRepository<Stat, Integer> {
  Stat findByUserAndGamemode(User user, Mode gamemode);
}
