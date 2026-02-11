package pe.nanamochi.banchus.services.infra.storage;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banchus.storage.type", havingValue = "s3")
public class S3FileStorageProvider implements FileStorageProvider {
  private final S3Client s3Client;
  private static final String CACHE_ROOT = "banchus_storage_cache";

  @Value("${banchus.storage.s3.bucket}")
  private String bucketName;

  @Override
  public void initialize(List<String> buckets) throws IOException {
    cleanCache();
  }

  @Override
  public byte[] read(String bucket, String key) {
    try {
      return s3Client.getObject(b -> b.bucket(bucketName).key(bucket + "/" + key)).readAllBytes();
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public void write(String bucket, String key, byte[] content) {
    s3Client.putObject(
        b -> b.bucket(bucketName).key(bucket + "/" + key), RequestBody.fromBytes(content));
  }

  @Override
  public void delete(String bucket, String key) {
    s3Client.deleteObject(b -> b.bucket(bucketName).key(bucket + "/" + key));
  }

  @Override
  public boolean exists(String bucket, String key) {
    try {
      s3Client.headObject(b -> b.bucket(bucketName).key(bucket + "/" + key));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Path getFileAsPath(String bucket, String key) {
    // We use a local temporary file because the current performance calculation
    // libraries require a physical file path to operate
    // Once osu-native-jar supports direct byte[] or InputStream processing,
    // this method and cleanCache() can be removed from the interface and implementations
    // Check PerformanceService.calculate()
    Path cachePath = Path.of(System.getProperty("java.io.tmpdir"), CACHE_ROOT, bucket, key);

    if (Files.exists(cachePath)) {
      return cachePath;
    }

    try {
      Files.createDirectories(cachePath.getParent());
      byte[] data = this.read(bucket, key);

      if (data == null) {
        throw new RuntimeException("File not found in S3: " + bucket + "/" + key);
      }

      Files.write(cachePath, data);
      return cachePath;
    } catch (IOException e) {
      throw new RuntimeException("Failed to cache S3 file locally: " + bucket + "/" + key, e);
    }
  }

  @PreDestroy
  public void cleanCache() throws IOException {
    Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), CACHE_ROOT);
    if (Files.exists(tempDir)) {
      try (var stream = Files.walk(tempDir)) {
        stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }
}
