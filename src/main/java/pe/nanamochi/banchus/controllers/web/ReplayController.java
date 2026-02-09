package pe.nanamochi.banchus.controllers.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.nanamochi.banchus.entities.db.Score;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.services.*;

@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class ReplayController {
  private final UserService userService;
  private final SessionService sessionService;
  private final ScoreService scoreService;
  private final ReplayService replayService;

  @GetMapping(value = "/osu-getreplay.php", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<byte[]> getReplay(
      @RequestParam(value = "u", required = false) String username,
      @RequestParam(value = "h", required = false) String passwordMd5,
      @RequestParam(value = "c", required = false) Integer scoreId) {
    User user = userService.login(username, passwordMd5);
    if (user == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Session session = sessionService.getPrimarySessionByUsername(user.getUsername());
    if (session == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Score score = scoreService.getScoreById(scoreId);
    if (score == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    byte[] replayData = replayService.getReplay(scoreId);
    if (replayData == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // TODO: increment replay views for this score, there are things to consider like:
    // - dont increase views fore the player watching their own replay
    // - manage a cooldown so people cant just spam refresh to increase views (use redis for this)

    return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(replayData);
  }
}
