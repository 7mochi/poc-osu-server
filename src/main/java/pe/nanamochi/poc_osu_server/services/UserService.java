package pe.nanamochi.poc_osu_server.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.poc_osu_server.entities.db.User;
import pe.nanamochi.poc_osu_server.repositories.UserRepository;
import pe.nanamochi.poc_osu_server.utils.Security;

import java.security.NoSuchAlgorithmException;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User login(String username, String passwordMd5) {
        return userRepository.findByUsernameAndPasswordMd5(username, passwordMd5);
    }

    public User createUser(String username, String passwordPlainText, String email, int country) throws NoSuchAlgorithmException {
        User user = new User();
        user.setUsername(username);
        user.setPasswordMd5(Security.getMd5(passwordPlainText));
        user.setEmail(email);
        user.setCountry(country);
        return userRepository.save(user);
    }
}
