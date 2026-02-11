package pe.nanamochi.banchus.controllers.statics;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.nanamochi.banchus.services.infra.AvatarService;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class AvatarController {
  private final AvatarService avatarService;

  @GetMapping("/{userId}")
  public ResponseEntity<?> getAvatar(@PathVariable String userId) {
    byte[] avatarData = null;
    if (NumberUtils.isDigits(userId)) {
      avatarData = avatarService.getAvatar(userId);
    }

    if (avatarData == null) {
      avatarData = avatarService.getAvatar("default");
    }

    return ResponseEntity.ok().header("Content-Type", "image/png").body(avatarData);
  }
}
