package pe.nanamochi.banchus.repositories;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.db.Session;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {}
