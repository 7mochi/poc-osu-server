package pe.nanamochi.banchus.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.services.UserService;
import pe.nanamochi.banchus.utils.OsuDirectApi;

@RestController
@RequestMapping("/d")
@RequiredArgsConstructor
public class BeatmapSetController {
  private final UserService userService;

  @GetMapping("/{beatmapSetId}")
  public ResponseEntity<String> downloadBeatmapSet(
      @PathVariable("beatmapSetId") String beatmapSetId,
      @RequestParam("u") String username,
      @RequestParam("h") String passwordMd5,
      @RequestParam("vv") int endpointVersion) {
    User user = userService.login(username, passwordMd5);
    if (user == null) {
      return ResponseEntity.status(401).body("-1\nInvalid username or password.");
    }

    return ResponseEntity.status(307)
        .header("Location", OsuDirectApi.BASE_URL + "/d/" + beatmapSetId)
        .build();
  }
}
