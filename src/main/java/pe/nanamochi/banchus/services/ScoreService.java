package pe.nanamochi.banchus.services;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.BeatmapRankedStatus;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.SubmissionStatus;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.ScoreRepository;

@Service
public class ScoreService {
  @Autowired private ScoreRepository scoreRepository;

  public Score saveScore(Score score) {
    return scoreRepository.save(score);
  }

  public Score updateScore(Score score) {
    if (!scoreRepository.existsById(score.getId())) {
      throw new IllegalArgumentException("Score not found: " + score.getId());
    }
    return scoreRepository.save(score);
  }

  public List<Score> getUserTop100(User user, Mode mode) {
    return scoreRepository
        .findTop100ByUserAndModeAndSubmissionStatusInAndBeatmapStatusInOrderByPerformancePointsDesc(
            user,
            mode,
            List.of(SubmissionStatus.BEST),
            List.of(BeatmapRankedStatus.RANKED, BeatmapRankedStatus.APPROVED));
  }

  public int getUserBestScoresCount(User user, Mode mode) {
    return scoreRepository.countByUserAndModeAndSubmissionStatusInAndBeatmapStatusIn(
        user,
        mode,
        List.of(SubmissionStatus.BEST),
        List.of(BeatmapRankedStatus.RANKED, BeatmapRankedStatus.APPROVED));
  }

  public Score getBestScore(Beatmap beatmap, User user) {
    return scoreRepository.findFirstByBeatmapAndUserAndSubmissionStatusOrderByPerformancePointsDesc(
        beatmap, user, SubmissionStatus.BEST);
  }
}
