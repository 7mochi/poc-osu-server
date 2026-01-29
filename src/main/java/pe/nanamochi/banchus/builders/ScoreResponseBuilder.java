package pe.nanamochi.banchus.builders;

import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.Stat;
import pe.nanamochi.banchus.entities.db.User;

/**
 * Builder for formatting score submission responses.
 * Responsible for:
 * - Building simple responses for quick submissions
 * - Building complete responses with before/after stats
 * - Formatting data for osu! client compatibility
 * - Managing achievement notifications
 *
 * This component centralizes response formatting logic, making it easy to update
 * the format without touching score submission logic.
 */
@Component
public class ScoreResponseBuilder {

  private static final Logger logger = LoggerFactory.getLogger(ScoreResponseBuilder.class);

  /**
   * Build a simple response for a score submission.
   * Used when quick feedback is needed without detailed stats.
   *
   * @param score The saved score
   * @param beatmap The beatmap
   * @param user The user
   * @param accuracy The accuracy percentage
   * @return Formatted response string
   */
  public String buildSimpleResponse(Score score, Beatmap beatmap, User user, double accuracy) {
    StringBuilder response = new StringBuilder();

    // Beatmap info
    Integer playcount = beatmap.getPlaycount();
    Integer passcount = beatmap.getPasscount();

    // Ensure playcount is never null or 0 (minimum 1 to avoid issues)
    if (playcount == null || playcount <= 0) {
      playcount = 1;
    }
    // Ensure passcount doesn't exceed playcount and is never negative
    if (passcount == null || passcount < 0 || passcount > playcount) {
      passcount = 0;
    }

    response.append("beatmapId:").append(beatmap.getBeatmapId()).append("|")
        .append("beatmapSetId:").append(beatmap.getBeatmapsetId()).append("|")
        .append("beatmapPlaycount:").append(playcount).append("|")
        .append("beatmapPasscount:").append(passcount).append("|")
        .append("approvedDate:").append(formatDateTime(beatmap.getLastUpdate())).append("|")
        .append("\n");

    // Beatmap ranking chart
    response.append("|chartId:beatmap|")
        .append("chartUrl:https://osu.ppy.sh/beatmapsets/").append(beatmap.getBeatmapsetId()).append("|")
        .append("chartName:Beatmap Ranking|")
        .append("rankBefore:|")
        .append("rankAfter:|")
        .append("rankedScoreBefore:|")
        .append("rankedScoreAfter:").append(score.getScore()).append("|")
        .append("totalScoreBefore:|")
        .append("totalScoreAfter:").append(score.getScore()).append("|")
        .append("maxComboBefore:|")
        .append("maxComboAfter:").append(score.getMaxCombo()).append("|")
        .append("accuracyBefore:|")
        .append("accuracyAfter:").append(String.format("%.2f", accuracy)).append("|")
        .append("ppBefore:|")
        .append("ppAfter:").append(String.format("%.2f", score.getPp())).append("|")
        .append("onlineScoreId:").append(score.getId()).append("|")
        .append("\n");

    // Overall ranking chart
    response.append("|chartId:overall|")
        .append("chartUrl:https://osu.ppy.sh/u/").append(user.getId()).append("|")
        .append("chartName:Overall Ranking|")
        .append("rankBefore:|")
        .append("rankAfter:|")
        .append("rankedScoreBefore:|")
        .append("rankedScoreAfter:|")
        .append("totalScoreBefore:|")
        .append("totalScoreAfter:|")
        .append("maxComboBefore:|")
        .append("maxComboAfter:|")
        .append("accuracyBefore:|")
        .append("accuracyAfter:|")
        .append("ppBefore:|")
        .append("ppAfter:|");

    // No achievements for now
    response.append("achievements-new:");

    return response.toString();
  }

  /**
   * Build a complete response with before/after stats like bancho.py.
   * Includes detailed ranking information and stat changes.
   *
   * @param score The saved score
   * @param beatmap The beatmap
   * @param user The user
   * @param statBefore The stats before this submission
   * @param statAfter The stats after this submission
   * @param globalRankBefore Global rank before
   * @param globalRankAfter Global rank after
   * @param previousBestScore Previous best score on this beatmap (may be null)
   * @return Formatted response string
   */
  public String buildCompleteResponse(
      Score score,
      Beatmap beatmap,
      User user,
      Stat statBefore,
      Stat statAfter,
      int globalRankBefore,
      int globalRankAfter,
      Score previousBestScore) {

    StringBuilder response = new StringBuilder();

    // Beatmap info
    response.append("beatmapId:").append(beatmap.getBeatmapId()).append("|")
        .append("beatmapSetId:").append(beatmap.getBeatmapsetId()).append("|")
        .append("beatmapPlaycount:").append(safeNull(beatmap.getPlaycount(), 0)).append("|")
        .append("beatmapPasscount:").append(safeNull(beatmap.getPasscount(), 0)).append("|")
        .append("approvedDate:").append(formatDateTime(beatmap.getLastUpdate())).append("|")
        .append("\n");

    // Beatmap ranking chart with before/after values
    String beatmapRankBefore = "";
    String beatmapRankAfter = "";
    int beatmapRankedScoreBefore = 0;
    int beatmapTotalScoreBefore = 0;
    int beatmapMaxComboBefore = 0;
    double beatmapAccuracyBefore = 0.0;
    int beatmapPpBefore = 0;

    // If there was a previous best score, use its values for "BEFORE"
    if (previousBestScore != null) {
      beatmapRankedScoreBefore = previousBestScore.getScore();
      beatmapTotalScoreBefore = previousBestScore.getScore();
      beatmapMaxComboBefore = previousBestScore.getMaxCombo();
      beatmapAccuracyBefore = previousBestScore.getAcc();
      beatmapPpBefore = Math.round(previousBestScore.getPp());
    }

    response.append("|chartId:beatmap|")
        .append("chartUrl:https://osu.ppy.sh/beatmapsets/").append(beatmap.getBeatmapsetId()).append("|")
        .append("chartName:Beatmap Ranking|")
        .append("rankBefore:").append(beatmapRankBefore).append("|")
        .append("rankAfter:").append(beatmapRankAfter).append("|")
        .append("rankedScoreBefore:").append(beatmapRankedScoreBefore).append("|")
        .append("rankedScoreAfter:").append(score.getScore()).append("|")
        .append("totalScoreBefore:").append(beatmapTotalScoreBefore).append("|")
        .append("totalScoreAfter:").append(score.getScore()).append("|")
        .append("maxComboBefore:").append(beatmapMaxComboBefore).append("|")
        .append("maxComboAfter:").append(score.getMaxCombo()).append("|")
        .append("accuracyBefore:")
        .append(beatmapAccuracyBefore > 0 ? String.format("%.2f", beatmapAccuracyBefore) : "")
        .append("|")
        .append("accuracyAfter:").append(String.format("%.2f", score.getAcc())).append("|")
        .append("ppBefore:")
        .append(beatmapPpBefore > 0 ? beatmapPpBefore : "")
        .append("|")
        .append("ppAfter:").append(Math.round(score.getPp())).append("|")
        .append("onlineScoreId:").append(score.getId()).append("|")
        .append("\n");

    // Overall ranking chart with user's before/after stats
    response.append("|chartId:overall|")
        .append("chartUrl:https://osu.ppy.sh/u/").append(user.getId()).append("|")
        .append("chartName:Overall Ranking|")
        .append("rankBefore:").append(globalRankBefore).append("|")
        .append("rankAfter:").append(globalRankAfter).append("|")
        .append("rankedScoreBefore:").append(statBefore.getRankedScore()).append("|")
        .append("rankedScoreAfter:").append(statAfter.getRankedScore()).append("|")
        .append("totalScoreBefore:").append(statBefore.getTotalScore()).append("|")
        .append("totalScoreAfter:").append(statAfter.getTotalScore()).append("|")
        .append("maxComboBefore:").append(statBefore.getHighestCombo()).append("|")
        .append("maxComboAfter:").append(statAfter.getHighestCombo()).append("|")
        .append("accuracyBefore:").append(String.format("%.2f", statBefore.getAccuracy())).append("|")
        .append("accuracyAfter:").append(String.format("%.2f", statAfter.getAccuracy())).append("|")
        .append("ppBefore:").append(statBefore.getPerformancePoints()).append("|")
        .append("ppAfter:").append(statAfter.getPerformancePoints()).append("|");

    // No achievements for now
    response.append("achievements-new:");

    // Log the changes
    logger.info("✓ Response built with BEFORE/AFTER stats:");
    logger.info("  Beatmap - PP: {}pp -> {}pp",
        beatmapPpBefore, Math.round(score.getPp()));
    logger.info("  Overall - Rank: {} -> {}", globalRankBefore, globalRankAfter);
    logger.info("  Overall - PP: {}pp -> {}pp",
        statBefore.getPerformancePoints(), statAfter.getPerformancePoints());
    logger.info("  Overall - Accuracy: {} -> {}",
        String.format("%.2f%%", statBefore.getAccuracy()),
        String.format("%.2f%%", statAfter.getAccuracy()));

    return response.toString();
  }

  /**
   * Format a LocalDateTime to string for response.
   * Returns empty string if null.
   *
   * @param dateTime The LocalDateTime to format
   * @return Formatted date string or empty string if null
   */
  private String formatDateTime(Object dateTime) {
    if (dateTime == null) {
      return "";
    }

    if (dateTime instanceof java.time.LocalDateTime) {
      return ((java.time.LocalDateTime) dateTime)
          .format(DateTimeFormatter.ISO_DATE_TIME);
    }

    return dateTime.toString();
  }

  /**
   * Safe null-checking utility for integers.
   * Returns the value if not null, otherwise returns the default.
   *
   * @param value The value to check
   * @param defaultValue The default if value is null
   * @return The value or default
   */
  private Integer safeNull(Integer value, int defaultValue) {
    return value != null ? value : defaultValue;
  }

  /**
   * Build an error response for client.
   *
   * @param errorMessage The error message
   * @return Formatted error response
   */
  public String buildErrorResponse(String errorMessage) {
    logger.warn("Building error response: {}", errorMessage);
    return "error: " + errorMessage;
  }

  /**
   * Build a success response with minimal data.
   * Used when we just need to confirm the score was saved.
   *
   * @param scoreId The ID of the saved score
   * @return Simple success response
   */
  public String buildSuccessResponse(Long scoreId) {
    logger.info("Building success response for score ID: {}", scoreId);
    return "score_id:" + scoreId;
  }
}
