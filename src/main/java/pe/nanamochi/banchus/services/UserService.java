package pe.nanamochi.banchus.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.User;
import pe.nanamochi.banchus.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;

  public User login(String username, String passwordMd5) {
    return userRepository.findByUsernameAndPasswordMd5(username, passwordMd5);
  }

  public User findByUsername(String username) {
    return userRepository.findByUsername(username);
  }

  public User findByEmail(String email) {
    return userRepository.findByEmail(email);
  }

  public User createUser(User user) {
    return userRepository.save(user);
  }

  public User updateUser(User user) {
    if (!userRepository.existsById(user.getId())) {
      throw new IllegalArgumentException("User not found: " + user.getUsername());
    }
    return userRepository.save(user);
  }
}
