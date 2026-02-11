package pe.nanamochi.banchus.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@ConditionalOnProperty(name = "banchus.storage.type", havingValue = "s3")
public class S3Config {
  @Value("${banchus.storage.s3.region}")
  private String region;

  @Value("${banchus.storage.s3.access-key}")
  private String accessKey;

  @Value("${banchus.storage.s3.secret-key}")
  private String secretKey;

  @Value("${banchus.storage.s3.endpoint}")
  private String endpoint;

  @Bean
  public S3Client s3Client() {
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));

    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));
    }
    builder.forcePathStyle(true);
    builder.serviceConfiguration(S3Configuration.builder().chunkedEncodingEnabled(false).build());

    return builder.build();
  }
}
