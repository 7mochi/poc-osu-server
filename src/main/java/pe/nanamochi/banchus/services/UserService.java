package pe.nanamochi.banchus.services;

import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.UserRepository;
import pe.nanamochi.banchus.utils.Security;

@Service
public class UserService {

  @Autowired private UserRepository userRepository;

  public User login(String username, String passwordMd5) {
    return userRepository.findByUsernameAndPasswordMd5(username, passwordMd5);
  }

  public User findByUsername(String username) {
    return userRepository.findByUsername(username);
  }

  public User findByEmail(String email) {
    return userRepository.findByEmail(email);
  }

  public User createUser(String username, String passwordPlainText, String email, int country)
      throws NoSuchAlgorithmException {
    User user = new User();
    user.setUsername(username);
    user.setPasswordMd5(Security.getMd5(passwordPlainText));
    user.setEmail(email);
    user.setCountry(country);
    return userRepository.save(user);
  }
}
