package pe.nanamochi.banchus.services;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.db.Session;
import pe.nanamochi.banchus.repositories.SessionRepository;

@Service
public class SessionService {
  @Autowired private SessionRepository sessionRepository;

  public Session getSessionByID(UUID id) {
    return sessionRepository.findById(id).orElse(null);
  }

  public List<Session> getAllSessions() {
    return sessionRepository.findAll();
  }

  public Session getPrimarySessionByUsername(String username) {
    List<Session> osuSessions = getAllSessions();
    for (Session session : osuSessions) {
      if (session.getUser().getUsername().equalsIgnoreCase(username)
          && session.isPrimarySession()) {
        return session;
      }
    }
    return null;
  }

  public Session saveSession(Session session) {
    return sessionRepository.save(session);
  }

  public Session updateSession(Session session) {
    if (!sessionRepository.existsById(session.getId())) {
      throw new IllegalArgumentException("Session not found: " + session.getId());
    }
    return sessionRepository.save(session);
  }

  public Session deleteSession(Session session) {
    if (!sessionRepository.existsById(session.getId())) {
      throw new IllegalArgumentException("Session not found: " + session.getId());
    }
    sessionRepository.delete(session);
    return session;
  }
}
