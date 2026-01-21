package pe.nanamochi.banchus.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.db.Session;

import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
}
