package pe.nanamochi.banchus.services;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.BeatmapRankedStatus;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.SubmissionStatus;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.ScoreRepository;

@Service
public class ScoreService {
  @Autowired private ScoreRepository scoreRepository;

  public Score getScoreById(Integer id) {
    return scoreRepository.findById(id).orElse(null);
  }

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

  public List<Score> getBeatmapLeaderboard(
      Beatmap beatmap, Mode mode, Integer mods, SubmissionStatus status, CountryCode country) {
    if (mods != null) {
      if (country != null) {
        return scoreRepository
            .findTop50ByBeatmapAndModeAndModsAndSubmissionStatusAndUser_RestrictedFalseAndUser_CountryOrderByScoreDesc(
                beatmap, mode, mods, status, country);
      }

      return scoreRepository
          .findTop50ByBeatmapAndModeAndModsAndSubmissionStatusAndUser_RestrictedFalseOrderByScoreDesc(
              beatmap, mode, mods, status);
    }

    if (country != null) {
      return scoreRepository
          .findTop50ByBeatmapAndModeAndSubmissionStatusAndUser_RestrictedFalseAndUser_CountryOrderByScoreDesc(
              beatmap, mode, status, country);
    }

    return scoreRepository
        .findTop50ByBeatmapAndModeAndSubmissionStatusAndUser_RestrictedFalseOrderByScoreDesc(
            beatmap, mode, status);
  }
}
