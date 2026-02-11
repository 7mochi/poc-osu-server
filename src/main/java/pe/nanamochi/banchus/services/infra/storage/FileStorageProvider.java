package pe.nanamochi.banchus.services.infra.storage;

import java.nio.file.Path;
import java.util.List;

public interface FileStorageProvider {
  void initialize(List<String> buckets);

  byte[] read(String bucket, String key);

  void write(String bucket, String key, byte[] content);

  void delete(String bucket, String key);

  boolean exists(String bucket, String key);

  Path resolvePath(String bucket, String key);
}
