package pe.nanamochi.banchus.services.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.config.StorageConfig;

@Service
@RequiredArgsConstructor
public class ReplayService {
  private final FileStorageService storage;

  public void saveReplay(long scoreId, byte[] replayBytes) {
    storage.write(StorageConfig.REPLAY, String.valueOf(scoreId), replayBytes);
  }

  public byte[] getReplay(long scoreId) {
    return storage.read(StorageConfig.REPLAY, String.valueOf(scoreId));
  }
}
