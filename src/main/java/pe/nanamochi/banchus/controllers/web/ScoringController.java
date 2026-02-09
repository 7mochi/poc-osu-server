package pe.nanamochi.banchus.controllers.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.nanamochi.banchus.entities.commons.ScoreSubmissionErrors;
import pe.nanamochi.banchus.entities.db.Beatmap;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.services.BeatmapService;
import pe.nanamochi.banchus.services.ScoreService;
import pe.nanamochi.banchus.services.SessionService;
import pe.nanamochi.banchus.services.UserService;

@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class ScoringController {
  private final UserService userService;
  private final SessionService sessionService;
  private final ScoreService scoreService;
  private final BeatmapService beatmapService;

  @PostMapping(
      value = "/osu-submit-modular-selector.php",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String scoreSubmission(
      HttpServletRequest request,
      @RequestParam(value = "ft", required = false) Integer failTime,
      @RequestParam(value = "iv", required = false) String ivB64,
      @RequestParam(value = "st", required = false) Integer scoreTime,
      @RequestParam(value = "pass", required = false) String passwordMd5,
      @RequestParam(value = "osuver", required = false) String osuVersion,
      @RequestParam(value = "s", required = false) String clientHashB64,
      @RequestPart(value = "i", required = false) MultipartFile flCheatScreenshot)
      throws Exception {
    ScoreService.ParsedScoreDTO parsedScore =
        scoreService.parseScoreData(request, ivB64, osuVersion, scoreTime, failTime);
    Score score = parsedScore.getScore();

    User user = userService.login(score.getUser().getUsername(), passwordMd5);
    if (user == null) {
      return "error: " + ScoreSubmissionErrors.NEEDS_AUTHENTICATION.getValue();
    }

    Session session = sessionService.getPrimarySessionByUsername(user.getUsername());
    if (session == null) {
      return "error: " + ScoreSubmissionErrors.NEEDS_AUTHENTICATION.getValue();
    }

    // TODO: handle differently depending on beatmap ranked status

    Beatmap beatmap = beatmapService.getOrCreateBeatmap(score.getBeatmap().getMd5());
    if (beatmap == null) {
      return "error: " + ScoreSubmissionErrors.BEATMAP_UNRANKED.getValue();
    }

    return scoreService.processScoreSubmission(parsedScore, user, beatmap, session);
  }
}
