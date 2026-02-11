package pe.nanamochi.banchus.services.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.nanamochi.banchus.config.StorageConfig;

@Service
@RequiredArgsConstructor
public class AvatarService {
  private final FileStorageService storage;

  public byte[] getAvatar(String userId) {
    return storage.read(StorageConfig.AVATAR, userId);
  }
}
