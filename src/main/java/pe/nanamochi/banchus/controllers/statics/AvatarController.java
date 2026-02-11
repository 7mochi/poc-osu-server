package pe.nanamochi.banchus.controllers.statics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.nanamochi.banchus.services.infra.StorageService;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class AvatarController {
  private final StorageService storageService;

  @GetMapping("/{userId}")
  public ResponseEntity<?> getAvatar(@PathVariable String userId) {
    return ResponseEntity.ok()
        .header("Content-Type", "image/png")
        .body(storageService.getAvatar(userId));
  }
}
