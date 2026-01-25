package pe.nanamochi.banchus.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.db.Channel;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, UUID> {
    List<Channel> findByAutoJoinTrue();
    Channel findByName(String name);
}
