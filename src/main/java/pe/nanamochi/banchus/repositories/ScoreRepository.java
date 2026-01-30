package pe.nanamochi.banchus.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.BeatmapRankedStatus;
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

  List<Score>
      findTop50ByBeatmapAndModeAndModsAndSubmissionStatusAndUser_RestrictedFalseOrderByScoreDesc(
          Beatmap beatmap, Mode mode, int mods, SubmissionStatus submissionStatus);
}
