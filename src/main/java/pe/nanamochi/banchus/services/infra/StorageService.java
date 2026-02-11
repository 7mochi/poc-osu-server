package pe.nanamochi.banchus.services.infra;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.services.infra.storage.FileStorageProvider;
import pe.nanamochi.banchus.utils.Security;

@Service
@RequiredArgsConstructor
public class StorageService {
  private final FileStorageProvider provider;
  private static final Logger logger = LoggerFactory.getLogger(StorageService.class);

  private static final String AVATARS = "avatars_files";
  private static final String BEATMAPS = "osu_beatmap_files";
  private static final String REPLAYS = "replays_files";
  private static final String SCREENSHOTS = "screenshots_files";

  public static final List<String> ALL_BUCKETS = List.of(AVATARS, BEATMAPS, REPLAYS, SCREENSHOTS);

  public void initStorage() {
    provider.initialize(ALL_BUCKETS);

    if (!provider.exists(AVATARS, "default.png")) {
      try (var is = getClass().getResourceAsStream("/images/default.png")) {
        if (is != null) {
          provider.write(AVATARS, "default.png", is.readAllBytes());
          logger.info("Default avatar initialized.");
        }
      } catch (IOException e) {
        logger.error("Failed to setup default avatar", e);
      }
    }
  }

  public byte[] getAvatar(String userId) {
    byte[] data = provider.read(AVATARS, userId + ".png");

    if (data == null) {
      return provider.read(AVATARS, "default.png");
    }
    return data;
  }

  public void uploadAvatar(String userId, byte[] content) {
    provider.write(AVATARS, userId + ".png", content);
  }

  public byte[] getBeatmap(int beatmapId) {
    return provider.read(BEATMAPS, beatmapId + ".osu");
  }

  public void uploadBeatmap(int beatmapId, byte[] content) {
    provider.write(BEATMAPS, beatmapId + ".osu", content);
  }

  public boolean beatmapExists(int beatmapId) {
    return provider.exists(BEATMAPS, beatmapId + ".osu");
  }

  public Path getBeatmapPath(int beatmapId) {
    return provider.resolvePath(BEATMAPS, beatmapId + ".osu");
  }

  public byte[] getReplay(long scoreId) {
    return provider.read(REPLAYS, scoreId + ".osr");
  }

  public void saveReplay(long scoreId, byte[] content) {
    provider.write(REPLAYS, scoreId + ".osr", content);
  }

  public byte[] getScreenshot(String screenshotId) {
    return provider.read(SCREENSHOTS, screenshotId + ".png");
  }

  public String saveScreenshot(byte[] content) {
    String id = Security.generateToken(6);
    provider.write(SCREENSHOTS, id + ".png", content);
    return id;
  }
}
