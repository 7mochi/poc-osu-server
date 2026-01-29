package pe.nanamochi.banchus.validators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.utils.ScoreDecryption.ParsedScore;

/**
 * Validator for score submissions.
 * Ensures that all score data is within valid ranges and consistent.
 */
@Component
public class ScoreValidator {

  private static final Logger logger = LoggerFactory.getLogger(ScoreValidator.class);

  /**
   * Validates a parsed score submission.
   * 
   * @param score The parsed score to validate
   * @throws IllegalArgumentException if the score is invalid
   */
  public void validateScore(ParsedScore score) throws IllegalArgumentException {
    // Validate accuracy
    if (score.accuracy < 0.0 || score.accuracy > 100.0) {
      throw new IllegalArgumentException(
          String.format("Invalid accuracy: %.2f%% (must be 0-100)", score.accuracy));
    }

    // Validate score value
    if (score.score < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid score: %d (must be >= 0)", score.score));
    }

    // Validate max combo
    if (score.maxCombo < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid max combo: %d (must be >= 0)", score.maxCombo));
    }

    // Validate hit counts
    if (score.n300 < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid 300s count: %d (must be >= 0)", score.n300));
    }
    if (score.n100 < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid 100s count: %d (must be >= 0)", score.n100));
    }
    if (score.n50 < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid 50s count: %d (must be >= 0)", score.n50));
    }
    if (score.nmiss < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid miss count: %d (must be >= 0)", score.nmiss));
    }

    // Validate mods value (should be a valid bitmask)
    if (score.mods < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid mods: %d (must be >= 0)", score.mods));
    }

    // Validate beatmap MD5
    if (score.mapMd5 == null || score.mapMd5.trim().isEmpty()) {
      throw new IllegalArgumentException("Beatmap MD5 cannot be null or empty");
    }

    if (!score.mapMd5.matches("^[a-f0-9]{32}$")) {
      throw new IllegalArgumentException(
          String.format("Invalid beatmap MD5: %s (must be 32 hex chars)", score.mapMd5));
    }

    // Validate game mode
    if (score.gamemode < 0 || score.gamemode > 3) {
      throw new IllegalArgumentException(
          String.format("Invalid gamemode: %d (must be 0-3)", score.gamemode));
    }

    logger.debug(
        "✓ Score validation passed: mapMd5={}, score={}, accuracy={:.2f}%, combo={}",
        score.mapMd5, score.score, score.accuracy, score.maxCombo);
  }

  /**
   * Validates minimum required fields for score submission.
   * Called before decryption to catch obvious issues early.
   *
   * @param scoreData The encrypted/encoded score data
   * @param username The username attempting to submit
   * @throws IllegalArgumentException if validation fails
   */
  public void validateScoreDataFormat(String scoreData, String username)
      throws IllegalArgumentException {
    if (scoreData == null || scoreData.trim().isEmpty()) {
      throw new IllegalArgumentException("Score data cannot be null or empty");
    }

    if (username == null || username.trim().isEmpty()) {
      throw new IllegalArgumentException("Username cannot be null or empty");
    }

    // Base64 validation: should contain only base64 chars and padding
    if (!scoreData.matches("^[A-Za-z0-9+/]*={0,2}$")) {
      throw new IllegalArgumentException(
          String.format("Invalid base64 format in score data (length: %d)", scoreData.length()));
    }

    logger.debug("✓ Score data format validation passed: username={}, length={}", username, scoreData.length());
  }
}
