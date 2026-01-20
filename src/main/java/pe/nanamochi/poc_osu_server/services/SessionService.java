package pe.nanamochi.poc_osu_server.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.nanamochi.poc_osu_server.entities.db.Session;
import pe.nanamochi.poc_osu_server.repositories.SessionRepository;

import java.util.UUID;

@Service
public class SessionService {
    @Autowired
    private SessionRepository sessionRepository;

    public Session getSessionByID(UUID id) {
        return sessionRepository.findById(id).orElse(null);
    }

    public Session saveSession(Session session) {
        return sessionRepository.save(session);
    }
}
