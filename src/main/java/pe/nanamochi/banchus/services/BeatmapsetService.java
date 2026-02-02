package pe.nanamochi.banchus.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.Beatmapset;
import pe.nanamochi.banchus.mappers.BeatmapsetMapper;
import pe.nanamochi.banchus.repositories.BeatmapsetRepository;

@Service
@RequiredArgsConstructor
public class BeatmapsetService {
  private final BeatmapsetRepository beatmapsetRepository;
  private final BeatmapsetMapper beatmapsetMapper;

  public Beatmapset create(Beatmapset beatmapset) {
    return beatmapsetRepository.save(beatmapset);
  }

  public Beatmapset createFromApi(pe.nanamochi.banchus.entities.osuapi.Beatmap beatmap) {
    return beatmapsetMapper.fromApi(beatmap);
  }

  public Beatmapset update(Beatmapset beatmapset) {
    if (!beatmapsetRepository.existsById(beatmapset.getId())) {
      throw new IllegalArgumentException("Beatmapset not found: " + beatmapset.getId());
    }
    return beatmapsetRepository.save(beatmapset);
  }

  public Beatmapset findByBeatmapsetId(int beatmapsetId) {
    return beatmapsetRepository.findById(beatmapsetId);
  }
}
