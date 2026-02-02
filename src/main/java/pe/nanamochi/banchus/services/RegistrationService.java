package pe.nanamochi.banchus.services;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.utils.Security;
import pe.nanamochi.banchus.utils.Validation;

@Service
@RequiredArgsConstructor
public class RegistrationService {
  private final UserService userService;
  private final StatService statService;

  public Map<String, List<String>> registerUser(
      String username, String email, String passwordPlainText, int check)
      throws NoSuchAlgorithmException {
    Map<String, List<String>> errors = new HashMap<>();

    if (check == 0) {
      if (!Validation.isValidUsername(username)) {
        addError(errors, "username", "Invalid username.");
      }
      if (!errors.containsKey("username") && userService.findByUsername(username) != null) {
        addError(errors, "username", "Username already taken by another player.");
      }
      if (!Validation.isValidEmail(email)) {
        addError(errors, "user_email", "Invalid email syntax.");
      } else if (userService.findByEmail(email) != null) {
        addError(errors, "user_email", "Email already taken by another player.");
      }
      if (!Validation.isValidPassword(passwordPlainText)) {
        addError(
            errors,
            "password",
            "Password must be between 8 and 32 characters and contain more than 3 unique"
                + " characters.");
      }

      if (!errors.isEmpty()) {
        return errors;
      }

      User user = new User();
      user.setUsername(username);
      user.setEmail(email);
      user.setPasswordMd5(Security.getMd5(passwordPlainText));
      user.setCountry(CountryCode.KP); // Default to North Korea for now
      user.setRestricted(false);

      User createdUser = userService.createUser(user);
      statService.createAllGamemodes(createdUser);
    }

    return Map.of();
  }

  private void addError(Map<String, List<String>> errors, String field, String message) {
    errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
  }

  private Map<String, String> flattenErrors(Map<String, List<String>> errors) {
    Map<String, String> flattened = new HashMap<>();
    for (var entry : errors.entrySet()) {
      flattened.put(entry.getKey(), String.join("\n", entry.getValue()));
    }
    return flattened;
  }
}
