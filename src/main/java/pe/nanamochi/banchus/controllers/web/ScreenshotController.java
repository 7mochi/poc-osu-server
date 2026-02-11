package pe.nanamochi.banchus.controllers.web;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.services.infra.StorageService;
import pe.nanamochi.banchus.services.player.UserService;

@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class ScreenshotController {
  private static final Logger logger = LoggerFactory.getLogger(ScreenshotController.class);

  private final UserService userService;
  private final StorageService storageService;

  @PostMapping("/osu-screenshot.php")
  public ResponseEntity<String> screenshotUploadHandler(
      @RequestParam(value = "u", required = false) String username,
      @RequestParam(value = "p", required = false) String passwordMd5,
      @RequestParam(value = "v", required = false) int endpointVersion,
      @RequestParam(value = "ss", required = false) MultipartFile screenshotFile)
      throws IOException {
    User user = userService.login(username, passwordMd5);
    if (user == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    if (screenshotFile == null || screenshotFile.getSize() > 4 * 1024 * 1024) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("screenshot file too large.");
    }

    if (endpointVersion != 1) {
      logger.warn(
          "Incorrect endpoint version for user {}: v{}", user.getUsername(), endpointVersion);
    }

    String filename = storageService.saveScreenshot(screenshotFile.getBytes());
    logger.info("User {} uploaded a screenshot, saved as {}", user.getUsername(), filename);
    return ResponseEntity.ok(filename);
  }
}
