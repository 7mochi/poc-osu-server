package pe.nanamochi.banchus.services;

import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.repositories.ScoreRepository;
import pe.nanamochi.banchus.utils.RankedStatusConverter;

/**
 * Service para manejar la lógica de leaderboards
 * Responsable de obtener scores formateados para el cliente osu!
 */
@Service
public class LeaderboardService {

  private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);

  @Autowired private ScoreRepository scoreRepository;

  @Autowired private ReplayService replayService;

  /**
   * Obtiene los scores ordenados por SCORE descendente (solo scores pasados)
   *
   * @param beatmapMd5 MD5 del beatmap
   * @return Lista de scores ordenados por SCORE
   */
  public List<Score> getScores(String beatmapMd5) {
    logger.debug("Fetching scores for beatmap: {}", beatmapMd5);
    List<Score> scores = scoreRepository.findByMapMd5AndStatusOrderByScoreDesc(beatmapMd5, 2);
    logger.debug("Found {} scores for beatmap {}", scores != null ? scores.size() : 0, beatmapMd5);
    return scores != null ? scores : List.of();
  }

  /**
   * Obtiene los scores ordenados por SCORE descendente, filtrados por modo de juego
   *
   * @param beatmapMd5 MD5 del beatmap
   * @param gameMode Modo de juego (0=std, 1=taiko, 2=ctb, 3=mania)
   * @return Lista de scores ordenados por SCORE
   */
  public List<Score> getScoresFiltered(String beatmapMd5, Integer gameMode) {
    logger.debug("Fetching scores for beatmap: {} (gameMode: {})", beatmapMd5, gameMode);
    List<Score> scores = scoreRepository.findByMapMd5AndStatusAndGameModeOrderByScoreDesc(beatmapMd5, 2, gameMode);
    logger.debug("Found {} scores for beatmap {} in game mode {}", 
        scores != null ? scores.size() : 0, beatmapMd5, gameMode);
    return scores != null ? scores : List.of();
  }

  /**
   * Formatea los scores en el formato esperado por el cliente osu!
   * Formato: username|score|maxcombo|count300|count100|count50|countmiss|countgeki|countkatu|perfect|mods|userid|rank|timestamp|hasreplay
   *
   * @param scores Lista de scores
   * @return String formateado en líneas separadas por \n
   */
  public String formatScoresResponse(List<Score> scores) {
    if (scores == null || scores.isEmpty()) {
      logger.debug("No scores to format");
      return "";
    }

    StringBuilder response = new StringBuilder();
    int rank = 1;

    for (Score score : scores) {
      response
          .append(score.getUser().getUsername())
          .append("|")
          .append(score.getScore())
          .append("|")
          .append(score.getMaxCombo())
          .append("|")
          .append(score.getN300())
          .append("|")
          .append(score.getN100())
          .append("|")
          .append(score.getN50())
          .append("|")
          .append(score.getNmiss())
          .append("|")
          .append(score.getNgeki())
          .append("|")
          .append(score.getNkatu())
          .append("|")
          .append(score.getPerfect() ? "1" : "0")
          .append("|")
          .append(score.getMods())
          .append("|")
          .append(score.getUser().getId())
          .append("|")
          .append(rank++)
          .append("|")
          .append(
              score
                  .getPlayTime()
                  .atZone(java.time.ZoneId.systemDefault())
                  .toInstant()
                  .getEpochSecond())
          .append("|")
          .append(replayService.replayExists(score.getId()) ? "1" : "0")
          .append("\n");
    }

    logger.debug("Formatted {} scores", scores.size());
    return response.toString();
  }

  /**
   * Formatea la respuesta completa del leaderboard en el formato esperado por osu!
   * Formato:
   * {ranked_status}|{serv_has_osz2}|{bid}|{bsid}|{len(scores)}|{fa_track_id}|{fa_license_text}
   * {offset}\n{beatmap_name}\n{rating}
   * {personal_best_score}
   * {leaderboard_scores}
   *
   * @param beatmap Beatmap para obtener información
   * @param scores Lista de scores del leaderboard
   * @return String formateado en el formato esperado por osu!
   */
  public String formatLeaderboardResponse(Beatmap beatmap, List<Score> scores) {
    if (beatmap == null) {
      logger.warn("Beatmap is null, returning empty response");
      return "";
    }

    StringBuilder response = new StringBuilder();

    // First line: ranked_status|serv_has_osz2|bid|bsid|len(scores)|fa_track_id|fa_license_text
    int rankedStatus = beatmap.getStatus() != null ? beatmap.getStatus() : 0;
    // Convert from API v2 format to web/leaderboard format
    int webRankedStatus = RankedStatusConverter.apiToWebStatus(rankedStatus);
    logger.debug(
        "Converting beatmap status from API ({} = {}) to web ({} = {})",
        rankedStatus,
        RankedStatusConverter.getApiStatusName(rankedStatus),
        webRankedStatus,
        RankedStatusConverter.getWebStatusName(webRankedStatus)
    );
    Integer beatmapId = beatmap.getBeatmapId() != null ? beatmap.getBeatmapId() : 0;
    Integer beatmapSetId = beatmap.getBeatmapsetId() != null ? beatmap.getBeatmapsetId() : 0;
    int scoreCount = scores != null ? scores.size() : 0;

    response
        .append(webRankedStatus)
        .append("|false|")
        .append(beatmapId)
        .append("|")
        .append(beatmapSetId)
        .append("|")
        .append(scoreCount)
        .append("|0|\n");

    // Second line: offset + beatmap_name + rating
    String beatmapName = String.format(
        "%s - %s [%s]",
        beatmap.getArtist() != null ? beatmap.getArtist() : "Unknown",
        beatmap.getTitle() != null ? beatmap.getTitle() : "Unknown",
        beatmap.getVersion() != null ? beatmap.getVersion() : "Unknown"
    );
    response.append("0\n").append(beatmapName).append("\n0.0\n");

    // Third line: personal best (empty for now since we don't track user's own score here)
    response.append("\n");

    // Rest: leaderboard scores
    if (scores != null && !scores.isEmpty()) {
      int rank = 1;
      for (Score score : scores) {
        response
            .append(score.getId())
            .append("|")
            .append(score.getUser().getUsername())
            .append("|")
            .append(score.getScore())
            .append("|")
            .append(score.getMaxCombo())
            .append("|")
            .append(score.getN50())
            .append("|")
            .append(score.getN100())
            .append("|")
            .append(score.getN300())
            .append("|")
            .append(score.getNmiss())
            .append("|")
            .append(score.getNkatu())
            .append("|")
            .append(score.getNgeki())
            .append("|")
            .append(score.getPerfect() ? "1" : "0")
            .append("|")
            .append(score.getMods())
            .append("|")
            .append(score.getUser().getId())
            .append("|")
            .append(rank++)
            .append("|")
            .append(
                score
                    .getPlayTime()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .getEpochSecond())
            .append("|")
            .append(replayService.replayExists(score.getId()) ? "1" : "0")
            .append("\n");
      }
      // Remove trailing newline
      if (response.length() > 0 && response.charAt(response.length() - 1) == '\n') {
        response.setLength(response.length() - 1);
      }
    }

    logger.debug("Formatted leaderboard response for {} scores", scoreCount);
    return response.toString();
  }
}
