package pe.nanamochi.banchus.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.User;

@Repository
public interface ScoreRepository extends JpaRepository<Score, Long> {
  List<Score> findByMapMd5AndStatusOrderByPpDesc(String mapMd5, Integer status);
  List<Score> findByMapMd5AndStatusOrderByScoreDesc(String mapMd5, Integer status);
  List<Score> findByMapMd5AndStatusAndGameModeOrderByScoreDesc(String mapMd5, Integer status, Integer gameMode);
  List<Score> findByMapMd5AndStatusAndGameModeOrderByPpDesc(String mapMd5, Integer status, Integer gameMode);
  List<Score> findByUserIdOrderByPlayTimeDesc(Integer userId);
  List<Score> findByMapMd5AndUserOrderByIdDesc(String mapMd5, User user);

  /**
   * Fetch all scores for a user in a specific game mode on ranked/approved beatmaps.
   * The StatService will filter to one score per beatmap (highest PP wins).
   * Used for calculating weighted accuracy and PP.
   * Only returns scores on RANKED or APPROVED beatmaps.
   * Ordered by PP descending to make filtering easier.
   * 
   * @param userId The user ID
   * @param gameMode The game mode
   * @return All best scores ordered by PP descending (will be filtered to 1 per beatmap in service)
   */
  @Query(value = """
    SELECT s.* FROM scores s
    JOIN maps m ON m.md5 = s.map_md5
    WHERE s.userid = :userId 
      AND s.game_mode = :gameMode 
      AND s.status = 2 
      AND s.map_md5 IS NOT NULL
    ORDER BY s.map_md5 ASC, s.pp DESC
    """, nativeQuery = true)
  List<Score> findBestScoresForUserInMode(@Param("userId") Long userId, @Param("gameMode") Integer gameMode);

  /**
   * Get the top scores on a specific beatmap ordered by PP (for beatmap leaderboard).
   * Only returns submitted scores (status = 2).
   * 
   * @param mapMd5 The beatmap MD5
   * @param gameMode The game mode
   * @return Top scores ordered by PP descending
   */
  @Query("SELECT s FROM Score s WHERE s.mapMd5 = :mapMd5 AND s.gameMode = :gameMode AND s.status = 2 ORDER BY s.pp DESC")
  List<Score> findTopScoresOnBeatmap(@Param("mapMd5") String mapMd5, @Param("gameMode") Integer gameMode);
}
