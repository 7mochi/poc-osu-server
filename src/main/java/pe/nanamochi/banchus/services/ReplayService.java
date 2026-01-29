package pe.nanamochi.banchus.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.repositories.ScoreRepository;

/**
 * Unified service for handling replay storage, retrieval, and validation.
 * Replays are stored as {scoreId}.osr files in a local directory.
 * Combines both save/retrieve operations and download validation.
 */
@Service
public class ReplayService {

  private static final Logger logger = LoggerFactory.getLogger(ReplayService.class);

  @Value("${replay.storage.path:.data/osr}")
  private String replayStoragePath;

  @Autowired private ScoreRepository scoreRepository;

  /**
   * Save a replay file.
   *
   * @param scoreId The score ID to use as filename (will be {scoreId}.osr)
   * @param replayData The binary replay data
   * @return true if successful, false otherwise
   */
  public boolean saveReplay(Long scoreId, byte[] replayData) {
    try {
      // Create directory if it doesn't exist
      Path dirPath = Paths.get(replayStoragePath);
      Files.createDirectories(dirPath);

      // Save replay file
      Path filePath = dirPath.resolve(scoreId + ".osr");
      Files.write(filePath, replayData);

      logger.info("✓ Replay saved: {} ({} bytes)", filePath, replayData.length);
      return true;

    } catch (IOException e) {
      logger.error("❌ Error saving replay for score {}: {}", scoreId, e.getMessage(), e);
      return false;
    }
  }

  /**
   * Retrieve a replay file.
   *
   * @param scoreId The score ID to retrieve
   * @return The binary replay data, or null if not found
   */
  public byte[] getReplay(Long scoreId) {
    try {
      Path filePath = Paths.get(replayStoragePath).resolve(scoreId + ".osr");

      if (!Files.exists(filePath)) {
        logger.warn("⚠️ Replay not found for score {}", scoreId);
        return null;
      }

      byte[] replayData = Files.readAllBytes(filePath);
      logger.info("✓ Replay retrieved: {} ({} bytes)", filePath, replayData.length);
      return replayData;

    } catch (IOException e) {
      logger.error("❌ Error retrieving replay for score {}: {}", scoreId, e.getMessage(), e);
      return null;
    }
  }

  /**
   * Check if a replay exists for a score.
   *
   * @param scoreId The score ID to check
   * @return true if replay exists, false otherwise
   */
  public boolean replayExists(Long scoreId) {
    Path filePath = Paths.get(replayStoragePath).resolve(scoreId + ".osr");
    return Files.exists(filePath);
  }

  /**
   * Delete a replay file.
   *
   * @param scoreId The score ID to delete
   * @return true if successful, false otherwise
   */
  public boolean deleteReplay(Long scoreId) {
    try {
      Path filePath = Paths.get(replayStoragePath).resolve(scoreId + ".osr");

      if (!Files.exists(filePath)) {
        logger.warn("⚠️ Replay not found for deletion: {}", scoreId);
        return false;
      }

      Files.delete(filePath);
      logger.info("✓ Replay deleted: {}", filePath);
      return true;

    } catch (IOException e) {
      logger.error("❌ Error deleting replay for score {}: {}", scoreId, e.getMessage(), e);
      return false;
    }
  }

  /**
   * Obtiene el contenido del replay para un score (con validación).
   *
   * @param scoreId ID del score
   * @return byte[] con los datos del replay, o null si no existe
   */
  public byte[] getReplayData(Long scoreId) {
    logger.debug("Retrieving replay for score: {}", scoreId);

    // Validar que el score existe
    Optional<Score> scoreOpt = scoreRepository.findById(scoreId);
    if (scoreOpt.isEmpty()) {
      logger.warn("Score not found: {}", scoreId);
      return null;
    }

    // Obtener replay
    byte[] replayData = getReplay(scoreId);
    if (replayData == null || replayData.length == 0) {
      logger.warn("No replay data found for score: {}", scoreId);
      return null;
    }

    logger.debug("Retrieved replay for score {}: {} bytes", scoreId, replayData.length);
    return replayData;
  }

  /**
   * Guarda un replay enviado por el cliente (con validación).
   *
   * @param scoreId ID del score
   * @param replayData bytes del replay
   * @return true si se guardó exitosamente, false si falló
   */
  public boolean saveReplayData(Long scoreId, byte[] replayData) {
    logger.debug("Saving replay for score: {} ({} bytes)", scoreId, replayData != null ? replayData.length : 0);

    // Validar que el score existe
    Optional<Score> scoreOpt = scoreRepository.findById(scoreId);
    if (scoreOpt.isEmpty()) {
      logger.warn("Cannot save replay for non-existent score: {}", scoreId);
      return false;
    }

    // Guardar replay
    boolean saved = saveReplay(scoreId, replayData);
    if (saved) {
      logger.debug("Replay saved successfully for score: {}", scoreId);
    } else {
      logger.warn("Failed to save replay for score: {}", scoreId);
    }

    return saved;
  }
}
