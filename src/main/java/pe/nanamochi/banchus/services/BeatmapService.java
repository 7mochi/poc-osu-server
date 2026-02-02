package pe.nanamochi.banchus.services;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.StorageType;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.entities.db.Beatmapset;
import pe.nanamochi.banchus.mappers.BeatmapMapper;
import pe.nanamochi.banchus.repositories.BeatmapRepository;
import pe.nanamochi.banchus.utils.OsuApi;

@Service
@RequiredArgsConstructor
public class BeatmapService {
  private final BeatmapRepository beatmapRepository;
  private final BeatmapMapper beatmapMapper;
  private final FileStorageService storage;
  private final OsuApi osuApi;
  private final BeatmapsetService beatmapsetService;

  public Beatmap create(Beatmap beatmap) {
    return beatmapRepository.save(beatmap);
  }

  public Beatmap createFromApi(pe.nanamochi.banchus.entities.osuapi.Beatmap beatmap) {
    return beatmapMapper.fromApi(beatmap);
  }

  public Beatmap update(Beatmap beatmap) {
    if (!beatmapRepository.existsById(beatmap.getId())) {
      throw new IllegalArgumentException("Beatmap not found: " + beatmap.getId());
    }
    return beatmapRepository.save(beatmap);
  }

  public Beatmap findByBeatmapId(int beatmapId) {
    return beatmapRepository.findById(beatmapId);
  }

  public Beatmap findByMd5(String md5) {
    return beatmapRepository.findByMd5(md5);
  }

  public byte[] getOrDownloadOsuFile(int beatmapId, String expectedMd5) {
    String fileKey = String.valueOf(beatmapId);

    if (storage.exists(StorageType.OSU, fileKey)) {
      byte[] localFile = storage.read(StorageType.OSU, fileKey);

      if (localFile != null) {
        if (expectedMd5 == null || calculateMd5(localFile).equalsIgnoreCase(expectedMd5)) {
          return localFile;
        }
      }
    }

    byte[] downloaded = osuApi.getOsuFile(beatmapId);
    if (downloaded == null) {
      return null;
    }

    storage.write(StorageType.OSU, fileKey, downloaded);
    return downloaded;
  }

  public Beatmap getOrCreateBeatmap(String beatmapMd5) {
    Beatmap beatmap = findByMd5(beatmapMd5);

    if (beatmap == null) {
      var osuApiBeatmap = osuApi.getBeatmap(beatmapMd5);
      if (osuApiBeatmap == null) return null;

      Beatmapset beatmapset = beatmapsetService.findByBeatmapsetId(osuApiBeatmap.getBeatmapsetId());
      if (beatmapset == null) {
        beatmapset = beatmapsetService.createFromApi(osuApiBeatmap);
        beatmapsetService.create(beatmapset);
      }

      List<pe.nanamochi.banchus.entities.osuapi.Beatmap> osuApiBeatmaps =
          osuApi.getBeatmaps(osuApiBeatmap.getBeatmapsetId());
      if (osuApiBeatmaps == null || osuApiBeatmaps.isEmpty()) return null;

      for (var b : osuApiBeatmaps) {
        Beatmap existing = findByMd5(b.getFileMd5());
        if (existing != null) {
          if (b.getFileMd5().equals(beatmapMd5)) beatmap = existing;
          continue;
        }

        Beatmap newBeatmap = createFromApi(b);
        newBeatmap.setBeatmapset(beatmapset);
        create(newBeatmap);
        getOrDownloadOsuFile(b.getBeatmapId(), b.getFileMd5());

        if (b.getFileMd5().equals(beatmapMd5)) beatmap = newBeatmap;
      }
    }

    beatmap = updateBeatmapIfOutdated(beatmap);

    return beatmap;
  }

  private Beatmap updateBeatmapIfOutdated(Beatmap beatmap) {
    List<pe.nanamochi.banchus.entities.osuapi.Beatmap> osuApiBeatmaps =
        osuApi.getBeatmaps(beatmap.getBeatmapset().getId());
    if (osuApiBeatmaps == null || osuApiBeatmaps.isEmpty()) return beatmap;

    Instant localLastUpdate = beatmap.getBeatmapset().getLastUpdated();
    Instant remoteLastUpdate =
        osuApiBeatmaps.stream()
            .map(pe.nanamochi.banchus.entities.osuapi.Beatmap::getLastUpdate)
            .max(Instant::compareTo)
            .orElse(localLastUpdate);

    if (localLastUpdate.isBefore(remoteLastUpdate)) {
      Beatmapset beatmapset = beatmap.getBeatmapset();
      beatmapset.setLastUpdated(remoteLastUpdate);
      beatmapsetService.update(beatmapset);

      // Actualizar cada beatmap
      for (var b : osuApiBeatmaps) {
        getOrDownloadOsuFile(b.getBeatmapId(), b.getFileMd5());

        Beatmap localBeatmap = findByMd5(b.getFileMd5());
        if (localBeatmap != null) {
          localBeatmap.setLastUpdated(b.getLastUpdate());
          localBeatmap.setStarRating(b.getDifficultyRating());
          update(localBeatmap);

          if (b.getFileMd5().equals(beatmap.getMd5())) {
            beatmap = localBeatmap;
          }
        }
      }
    }

    return beatmap; // Retorna siempre el beatmap correspondiente al MD5 original
  }

  private String calculateMd5(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return HexFormat.of().formatHex(md.digest(data));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to compute MD5", e);
    }
  }
}
