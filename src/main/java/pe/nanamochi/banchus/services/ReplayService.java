package pe.nanamochi.banchus.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.entities.StorageType;

@Service
@RequiredArgsConstructor
public class ReplayService {
  private final FileStorageService storage;

  public void saveReplay(long scoreId, byte[] replayBytes) {
    storage.write(StorageType.REPLAY, String.valueOf(scoreId), replayBytes);
  }

  public byte[] getReplay(long scoreId) {
    return storage.read(StorageType.REPLAY, String.valueOf(scoreId));
  }
}
