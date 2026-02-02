package pe.nanamochi.banchus.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.nanamochi.banchus.entities.BeatmapDirectDisplayMode;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.services.OsuDirectApiService;
import pe.nanamochi.banchus.services.UserService;

@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class DirectController {
  private final OsuDirectApiService osuDirectApiService;
  private final UserService userService;

  @GetMapping(value = "/osu-search.php", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> osuSearchHandler(
      @RequestParam("u") String username,
      @RequestParam("h") String passwordMd5,
      @RequestParam("r") BeatmapDirectDisplayMode displayMode,
      @RequestParam("p") int pageOffset,
      @RequestParam("q") String query,
      @RequestParam("m") int mode) {
    User user = userService.login(username, passwordMd5);
    if (user == null) {
      return ResponseEntity.status(401).body("-1\nInvalid username or password.");
    }

    String result = osuDirectApiService.search(query, mode, displayMode, pageOffset);
    if (result == null) {
      return ResponseEntity.ok("-1\nFailed to retrieve data from the beatmap mirror.");
    }

    return ResponseEntity.ok(result);
  }
}
