package pe.nanamochi.banchus.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.nanamochi.banchus.entities.DirectDisplayMode;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.services.UserService;
import pe.nanamochi.banchus.utils.OsuDirectApi;

@RestController
@RequestMapping("/web")
public class DirectController {
  private static final Logger logger = LoggerFactory.getLogger(DirectController.class);

  @Autowired private UserService userService;

  @GetMapping(value = "/osu-search.php", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> osuSearchHandler(
      @RequestParam("u") String username,
      @RequestParam("h") String passwordMd5,
      @RequestParam("r") DirectDisplayMode displayMode,
      @RequestParam("p") int pageOffset,
      @RequestParam("q") String query,
      @RequestParam("m") int mode) {
    User user = userService.login(username, passwordMd5);
    if (user == null) {
      return ResponseEntity.status(401).body("-1\nInvalid username or password.");
    }

    String result = OsuDirectApi.search(query, mode, displayMode, pageOffset);
    if (result == null) {
      return ResponseEntity.ok("-1\nFailed to retrieve data from the beatmap mirror.");
    }

    String[] parts = result.split("\\R", 2);
    int length = Integer.parseInt(parts[0]);
    // if we get 100 matches, send 101 to inform the client there are more to get
    if (length == 100) {
      result = "101\n" + parts[1];
    }

    return ResponseEntity.ok(result);
  }
}
