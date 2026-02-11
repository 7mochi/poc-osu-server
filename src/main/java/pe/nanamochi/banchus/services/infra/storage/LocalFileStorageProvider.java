package pe.nanamochi.banchus.services.infra.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalFileStorageProvider implements FileStorageProvider {
  private final String basePath = ".data/";

  @Override
  public void initialize(List<String> buckets) {
    buckets.forEach(
        bucket -> {
          try {
            Path path = Path.of(basePath, bucket);
            if (!Files.exists(path)) {
              Files.createDirectories(path);
            }
          } catch (IOException e) {
            throw new RuntimeException("Could not create directory: " + bucket, e);
          }
        });
  }

  @Override
  public byte[] read(String bucket, String key) {
    try {
      return Files.readAllBytes(Path.of(basePath, bucket, key));
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public void write(String bucket, String key, byte[] content) {
    try {
      Path path = Path.of(basePath, bucket, key);
      Files.createDirectories(path.getParent());
      Files.write(path, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void delete(String bucket, String key) {
    try {
      Files.deleteIfExists(Path.of(basePath, bucket, key));
    } catch (IOException ignored) {
    }
  }

  @Override
  public boolean exists(String bucket, String key) {
    return Files.exists(Path.of(basePath, bucket, key));
  }

  @Override
  public Path resolvePath(String bucket, String key) {
    return Path.of(basePath, bucket, key);
  }
}
