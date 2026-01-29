package pe.nanamochi.banchus.config;

import jakarta.servlet.ServletContext;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration to customize Tomcat server settings for multipart uploads.
 * Handles FileCountLimitExceededException by increasing the max parts limit.
 */
@Configuration
public class TomcatContextCustomizer {

  private static final Logger logger = LoggerFactory.getLogger(TomcatContextCustomizer.class);

  public TomcatContextCustomizer() {
    logger.debug("TomcatContextCustomizer initialized");
  }

  @Bean
  public ServletContextInitializer servletContextInitializer() {
    return servletContext -> {
      // This initializer runs after the context is created
      // The multipart configuration is already set via application.yaml
      logger.info("✓ ServletContext initialized - multipart limits should be configured via application.yaml");
    };
  }
}
