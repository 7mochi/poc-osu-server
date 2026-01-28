package pe.nanamochi.banchus.repositories;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.nanamochi.banchus.entities.db.Channel;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, UUID> {
  List<Channel> findByAutoJoin(boolean autoJoin);

  Channel findByName(String name);
}
