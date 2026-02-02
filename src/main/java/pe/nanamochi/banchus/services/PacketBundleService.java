package pe.nanamochi.banchus.services;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.PacketBundle;
import pe.nanamochi.banchus.repositories.PacketBundleRepository;

@Service
@RequiredArgsConstructor
public class PacketBundleService {
  private final PacketBundleRepository packetBundleRepository;

  public void enqueue(UUID sessionId, PacketBundle packetBundle) {
    packetBundleRepository.enqueue(sessionId, packetBundle);
  }

  public PacketBundle dequeueOne(UUID sessionId) {
    return packetBundleRepository.dequeueOne(sessionId);
  }

  public List<PacketBundle> dequeueAll(UUID sessionId) {
    return packetBundleRepository.dequeueAll(sessionId);
  }
}
