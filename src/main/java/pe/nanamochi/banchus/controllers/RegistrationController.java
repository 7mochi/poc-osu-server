package pe.nanamochi.banchus.controllers;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.services.StatService;
import pe.nanamochi.banchus.services.UserService;
import pe.nanamochi.banchus.utils.Security;
import pe.nanamochi.banchus.utils.Validation;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class RegistrationController {
  private final UserService userService;
  private final StatService statService;

  @PostMapping(value = "/users")
  public ResponseEntity<String> registerAccount(
      @RequestHeader MultiValueMap<String, String> headers,
      @RequestParam MultiValueMap<String, String> paramMap)
      throws NoSuchAlgorithmException {
    if (!paramMap.containsKey("user[username]")
        || !paramMap.containsKey("user[user_email]")
        || !paramMap.containsKey("user[password]")) {
      return ResponseEntity.badRequest().body("Missing required params");
    }

    String username = paramMap.get("user[username]").getFirst();
    String email = paramMap.get("user[user_email]").getFirst();
    String passwordPlainText = paramMap.get("user[password]").getFirst();
    int check = Integer.parseInt(paramMap.get("check").getFirst());

    if (check == 0) {
      HashMap<String, String> errors = new HashMap<>();
      if (!Validation.isValidUsername(username)) {
        errors.put("username", "Invalid username.");
      }
      if (userService.findByUsername(username) != null) {
        errors.put("username", "Username already taken by another player.");
      }
      if (!Validation.isValidEmail(email)) {
        errors.put("user_email", "Invalid email syntax.");
      }
      if (userService.findByEmail(email) != null) {
        errors.put("user_email", "Email already taken by another player.");
      }
      if (!Validation.isValidPassword(passwordPlainText)) {
        errors.put(
            "password",
            "Password must be between 8 and 32 characters and contain more than 3 unique"
                + " characters.");
      }

      if (!errors.isEmpty()) {
        StringBuilder responseBody = new StringBuilder("{\"form_error\": {\"user\": {");
        int count = 0;
        for (String field : errors.keySet()) {
          if (count > 0) {
            responseBody.append(", ");
          }
          responseBody
              .append("\"")
              .append(field)
              .append("\": [\"")
              .append(errors.get(field))
              .append("\"]");
          count++;
        }
        responseBody.append("}}}");
        return ResponseEntity.badRequest().body(responseBody.toString());
      }

      User user = new User();
      user.setUsername(username);
      user.setEmail(email);
      user.setPasswordMd5(Security.getMd5(passwordPlainText));
      user.setCountry(CountryCode.KP); // Default to North Korea for now
      user.setRestricted(false);
      user = userService.createUser(user);
      statService.createAllGamemodes(user);
    }

    return ResponseEntity.ok("ok");
  }
}
