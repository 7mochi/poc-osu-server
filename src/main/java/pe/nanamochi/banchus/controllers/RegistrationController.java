package pe.nanamochi.banchus.controllers;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import pe.nanamochi.banchus.services.RegistrationService;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class RegistrationController {
  private final RegistrationService registrationService;

  @PostMapping(value = "/users")
  public ResponseEntity<?> registerAccount(@RequestParam MultiValueMap<String, String> params)
      throws NoSuchAlgorithmException {
    if (!params.containsKey("user[username]")
        || !params.containsKey("user[user_email]")
        || !params.containsKey("user[password]")
        || !params.containsKey("check")) {
      return ResponseEntity.badRequest().body("Missing required params");
    }

    String username = params.getFirst("user[username]");
    String email = params.getFirst("user[user_email]");
    String passwordPlainText = params.getFirst("user[password]");
    int check = Integer.parseInt(params.getFirst("check"));

    Map<String, List<String>> errors =
        registrationService.registerUser(username, email, passwordPlainText, check);

    if (!errors.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("form_error", Map.of("user", errors)));
    }

    return ResponseEntity.ok("ok");
  }
}
