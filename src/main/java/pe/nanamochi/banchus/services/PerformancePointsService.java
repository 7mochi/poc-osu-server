package pe.nanamochi.banchus.services;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import io.github.nanamochi.rosu_pp_jar.Mods;
import io.github.nanamochi.rosu_pp_jar.Performance;
import io.github.nanamochi.rosu_pp_jar.PerformanceAttributes;
import io.github.nanamochi.rosu_pp_jar.RosuException;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.repositories.BeatmapRepository;

/**
 * Service for calculating Performance Points (PP) from score data.
 * Uses rosu-pp-jar (Rust binding) for accurate PP calculations.
 */
@Service
public class PerformancePointsService {

  private static final Logger logger = LoggerFactory.getLogger(PerformancePointsService.class);

  @Autowired
  private BeatmapRepository beatmapRepository;

  @Value("${beatmap.cache.directory:/tmp/banchus-beatmaps}")
  private String beatmapCacheDir;

  private static final String OSU_BEATMAP_URL = "https://osu.ppy.sh/osu/";

  public double calculatePP(
      String mapMd5,
      int n300,
      int n100,
      int n50,
      int nmiss,
      int maxCombo,
      int mods,
      double accuracy,
      int gameMode) {

    try {
      logger.info("📊 Calculating PP using rosu-pp-jar:");
      logger.info("  - Beatmap MD5: {}", mapMd5);
      logger.info("  - Hits: 300={}, 100={}, 50={}, Misses={}", n300, n100, n50, nmiss);
      logger.info("  - Max Combo: {} | Accuracy: {}%", maxCombo, String.format("%.2f", accuracy));

      Optional<Beatmap> beatmapOpt = beatmapRepository.findByMd5(mapMd5);
      if (beatmapOpt.isEmpty()) {
        logger.warn("⚠️ Beatmap not found in database for MD5: {}", mapMd5);
        return 0.0;
      }

      Beatmap beatmap = beatmapOpt.get();
      logger.info("✓ Beatmap found: {} - {} [{}]", 
          beatmap.getArtist(), beatmap.getTitle(), beatmap.getVersion());

      File osuFile = getOrDownloadBeatmapFile(beatmap);
      if (osuFile == null || !osuFile.exists()) {
        logger.warn("⚠️ Could not get beatmap file");
        return 0.0;
      }

      logger.info("✓ Beatmap file ready: {}", osuFile.getAbsolutePath());

      double pp = calculatePPWithRosuPp(
          osuFile,
          n300, n100, n50, nmiss,
          maxCombo, mods, accuracy, gameMode
      );

      logger.info("✓ PP calculated: {}", String.format("%.2f", pp));
      return pp;

    } catch (Exception e) {
      logger.error("❌ Error calculating PP: {}", e.getMessage());
      e.printStackTrace();
      return 0.0;
    }
  }

  private double calculatePPWithRosuPp(
      File osuFile,
      int n300, int n100, int n50, int nmiss,
      int maxCombo, int mods,
      double accuracy, int gameMode) {

    try {
      logger.info("🔧 Calculating PP using rosu-pp-jar API...");
      logger.debug("  Input parameters: n300={}, n100={}, n50={}, nmiss={}, combo={}, mods={}, acc={}", 
          n300, n100, n50, nmiss, maxCombo, mods, accuracy);

      // Load beatmap from file using rosu-pp-jar
      io.github.nanamochi.rosu_pp_jar.Beatmap beatmap = 
          io.github.nanamochi.rosu_pp_jar.Beatmap.fromPath(osuFile.getAbsolutePath());

      logger.info("✓ Beatmap loaded by rosu-pp-jar");
      
      // Create Performance calculator
      Performance performance = Performance.create(beatmap);
      logger.debug("✓ Performance calculator created");

      // Set mods if any - MUST be done first!
      if (mods > 0) {
        Mods modsObj = Mods.fromBits(mods);
        performance.setMods(modsObj);
        logger.debug("✓ Mods set: {} (bits)", mods);
      }

      // Set the accuracy calculated from hits (matches what banchopy does)
      performance.setAccuracy(accuracy);
      logger.debug("✓ Accuracy set: {}", String.format("%.2f", accuracy));

      // Set combo
      performance.setCombo(maxCombo);
      logger.debug("✓ Combo set: {}", maxCombo);

      // Set misses - this is critical for PP calculation
      performance.setMisses(nmiss);
      logger.debug("✓ Misses set: {}", nmiss);

      logger.info("✓ Score parameters set (SAME AS banchopy):");
      logger.info("    accuracy: {} (derived from n300={}, n100={}, n50={}, nmiss={})", 
          String.format("%.2f", accuracy), n300, n100, n50, nmiss);
      logger.info("    combo: {}", maxCombo);
      logger.info("    misses: {}", nmiss);
      logger.info("    mods: {}", mods);

      // Calculate PP
      PerformanceAttributes perfAttrs = performance.calculate();
      double pp = perfAttrs.pp();
      
      logger.debug("  - Raw PP value from rosu-pp: {}", String.format("%.2f", pp));
      logger.debug("  - Stars: {}", String.format("%.2f", perfAttrs.difficultyAttributes().stars()));

      logger.info("✓ PP calculated successfully: {} pp at {} stars", 
          String.format("%.2f", pp),
          String.format("%.2f", perfAttrs.difficultyAttributes().stars()));
      return pp;

    } catch (RosuException e) {
      logger.error("❌ RosuException while calculating PP: {}", e.getMessage());
      logger.error("   Rosu error details: {}", e);
      logger.debug("   Full exception: ", e);
      return 0.0;
    } catch (Exception e) {
      logger.error("❌ Unexpected error calculating PP: {} - {}", e.getClass().getName(), e.getMessage());
      logger.debug("   Full stack trace: ", e);
      return 0.0;
    }
  }

  private File getOrDownloadBeatmapFile(Beatmap beatmap) {
    try {
      Path cachePath = Paths.get(beatmapCacheDir);
      Files.createDirectories(cachePath);

      File cachedFile = cachePath.resolve(beatmap.getMd5() + ".osu").toFile();

      if (cachedFile.exists() && cachedFile.length() > 0) {
        logger.info("✓ Using cached beatmap file ({}B)", cachedFile.length());
        return cachedFile;
      }

      // Delete corrupted cache file if it exists and is 0 bytes
      if (cachedFile.exists() && cachedFile.length() == 0) {
        logger.warn("⚠️ Found corrupted cache file (0 bytes), removing");
        cachedFile.delete();
      }

      Integer beatmapId = beatmap.getBeatmapId();
      if (beatmapId == null || beatmapId == 0) {
        logger.warn("⚠️ Beatmap ID not available");
        return null;
      }

      String downloadUrl = OSU_BEATMAP_URL + beatmapId;
      logger.info("📥 Downloading from: {}", downloadUrl);

      if (!downloadFile(downloadUrl, cachedFile)) {
        logger.error("❌ Failed to download beatmap file");
        // Delete the corrupted/empty file
        if (cachedFile.exists()) {
          cachedFile.delete();
        }
        return null;
      }

      if (cachedFile.exists() && cachedFile.length() > 0) {
        logger.info("✓ Beatmap cached ({}B)", cachedFile.length());
        return cachedFile;
      }

      logger.error("❌ Beatmap file is empty after download");
      if (cachedFile.exists()) {
        cachedFile.delete();
      }
      return null;

    } catch (IOException e) {
      logger.error("❌ Error getting beatmap file: {}", e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("deprecation")
  private boolean downloadFile(String urlString, File destination) {
    int maxRetries = 3;
    int retryDelay = 1000; // Start with 1 second
    
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        logger.info("📥 Download attempt {}/{} from: {}", attempt, maxRetries, urlString);
        
        var conn = new URL(urlString).openConnection();
        conn.setConnectTimeout(15000); // 15 second timeout
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "banchus/1.0");
        
        // Check response code
        if (conn instanceof java.net.HttpURLConnection) {
          java.net.HttpURLConnection httpConn = (java.net.HttpURLConnection) conn;
          int responseCode = httpConn.getResponseCode();
          logger.info("   Response code: {}", responseCode);
          
          if (responseCode != 200) {
            logger.warn("⚠️ HTTP error {} on attempt {}/{}", responseCode, attempt, maxRetries);
            if (attempt < maxRetries) {
              Thread.sleep(retryDelay);
              retryDelay *= 2; // Exponential backoff
              continue;
            }
            return false;
          }
        }

        try (var in = conn.getInputStream()) {
          long bytesCopied = Files.copy(in, destination.toPath());
          logger.info("✓ Downloaded {} bytes", bytesCopied);
          
          if (bytesCopied == 0) {
            logger.warn("⚠️ Downloaded file is empty on attempt {}/{}", attempt, maxRetries);
            if (attempt < maxRetries) {
              destination.delete();
              Thread.sleep(retryDelay);
              retryDelay *= 2;
              continue;
            }
            return false;
          }
          
          return true;
        }
        
      } catch (InterruptedException e) {
        logger.warn("⚠️ Download interrupted on attempt {}/{}", attempt, maxRetries);
        Thread.currentThread().interrupt();
        return false;
      } catch (IOException e) {
        logger.warn("⚠️ Error downloading beatmap on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());
        
        if (attempt < maxRetries) {
          try {
            Thread.sleep(retryDelay);
            retryDelay *= 2;
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
          }
        }
      }
    }
    
    logger.error("❌ Failed to download beatmap after {} attempts", maxRetries);
    return false;
  }

  @Cacheable(value = "beatmapStars", key = "#mapMd5")
  public double getCachedStarRating(String mapMd5) {
    return 0.0;
  }

  public double calculatePPFromStars(
      double starRating, int n300, int n100, int n50, int nmiss,
      int maxCombo, int mods, double accuracy, int maxComboOnBeatmap) {
    return 0.0;
  }
}
