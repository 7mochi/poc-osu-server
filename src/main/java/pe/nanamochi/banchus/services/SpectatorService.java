package pe.nanamochi.banchus.services;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.repositories.SpectatorRepository;

@Service
@RequiredArgsConstructor
public class SpectatorService {
  private final SpectatorRepository spectatorRepository;

  public UUID add(UUID hostSessionId, UUID sessionId) {
    return spectatorRepository.add(hostSessionId, sessionId);
  }

  public UUID remove(UUID hostSessionId, UUID sessionId) {
    return spectatorRepository.remove(hostSessionId, sessionId);
  }

  public Set<UUID> getMembers(UUID channelId) {
    return spectatorRepository.getMembers(channelId);
  }
}
