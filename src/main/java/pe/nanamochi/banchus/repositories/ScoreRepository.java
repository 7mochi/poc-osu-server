package pe.nanamochi.banchus.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.BeatmapRankedStatus;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.SubmissionStatus;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.User;

@Repository
public interface ScoreRepository extends JpaRepository<Score, Integer> {
  List<Score>
      findTop100ByUserAndModeAndSubmissionStatusInAndBeatmapStatusInOrderByPerformancePointsDesc(
          User user,
          Mode mode,
          List<SubmissionStatus> submissionStatuses,
          List<BeatmapRankedStatus> beatmapRankedStatuses);

  int countByUserAndModeAndSubmissionStatusInAndBeatmapStatusIn(
      User user,
      Mode mode,
      List<SubmissionStatus> submissionStatuses,
      List<BeatmapRankedStatus> beatmapRankedStatuses);

  Score findFirstByBeatmapAndUserAndSubmissionStatusOrderByPerformancePointsDesc(
      Beatmap beatmap, User user, SubmissionStatus submissionStatus);

  @Query(
      """
        SELECT s
        FROM Score s
        WHERE s.beatmap = :beatmap
          AND s.mode = :mode
          AND s.submissionStatus = :status
          AND FUNCTION('bitand', s.user.privileges, 1) <> 0
        ORDER BY s.score DESC
      """)
  List<Score> findTop50Unrestricted(
      Beatmap beatmap, Mode mode, @Param("status") SubmissionStatus submissionStatus);

  @Query(
      """
        SELECT s
        FROM Score s
        WHERE s.beatmap = :beatmap
          AND s.mode = :mode
          AND s.submissionStatus = :status
          AND s.user.country = :country
          AND FUNCTION('bitand', s.user.privileges, 1) <> 0
        ORDER BY s.score DESC
      """)
  List<Score> findTop50UnrestrictedByCountry(
      Beatmap beatmap,
      Mode mode,
      @Param("status") SubmissionStatus submissionStatus,
      CountryCode country);

  @Query(
      """
        SELECT s
        FROM Score s
        WHERE s.beatmap = :beatmap
          AND s.mode = :mode
          AND s.mods = :mods
          AND s.submissionStatus = :status
          AND FUNCTION('bitand', s.user.privileges, 1) <> 0
        ORDER BY s.score DESC
      """)
  List<Score> findTop50UnrestrictedWithMods(
      Beatmap beatmap, Mode mode, int mods, @Param("status") SubmissionStatus submissionStatus);

  @Query(
      """
        SELECT s
        FROM Score s
        WHERE s.beatmap = :beatmap
          AND s.mode = :mode
          AND s.mods = :mods
          AND s.submissionStatus = :status
          AND s.user.country = :country
          AND FUNCTION('bitand', s.user.privileges, 1) <> 0
        ORDER BY s.score DESC
      """)
  List<Score> findTop50UnrestrictedWithModsByCountry(
      Beatmap beatmap,
      Mode mode,
      int mods,
      @Param("status") SubmissionStatus submissionStatus,
      CountryCode country);
}
