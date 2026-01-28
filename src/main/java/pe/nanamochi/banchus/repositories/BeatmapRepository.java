package pe.nanamochi.banchus.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.db.Beatmap;

@Repository
public interface BeatmapRepository extends JpaRepository<Beatmap, Integer> {
  Optional<Beatmap> findByMd5(String md5);
  Optional<Beatmap> findByBeatmapId(Integer beatmapId);
}
