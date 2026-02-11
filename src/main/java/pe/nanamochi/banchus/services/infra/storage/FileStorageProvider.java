package pe.nanamochi.banchus.services.infra.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FileStorageProvider {
  void initialize(List<String> buckets) throws IOException;

  byte[] read(String bucket, String key);

  void write(String bucket, String key, byte[] content);

  void delete(String bucket, String key);

  boolean exists(String bucket, String key);

  Path getFileAsPath(String bucket, String key);
}
