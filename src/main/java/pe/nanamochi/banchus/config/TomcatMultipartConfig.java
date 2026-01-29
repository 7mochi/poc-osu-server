package pe.nanamochi.banchus.config;

import jakarta.servlet.ServletRegistration;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Tomcat multipart request handling.
 * 
 * Handles FileCountLimitExceededException by configuring servlet multipart limits.
 */
@Configuration
public class TomcatMultipartConfig {

  @Bean
  public ServletContextInitializer multipartConfigInitializer() {
    return servletContext -> {
      ServletRegistration.Dynamic dispatcher =
          (ServletRegistration.Dynamic) servletContext.getServletRegistration("dispatcherServlet");
      if (dispatcher != null) {
        // Create multipart config with generous limits for score submissions with replay attachments
        jakarta.servlet.MultipartConfigElement config = 
            new jakarta.servlet.MultipartConfigElement(
                null, // location (default temp dir)
                52428800, // max file size: 50MB
                52428800, // max request size: 50MB
                1048576); // file size threshold: 1MB
        
        dispatcher.setMultipartConfig(config);
      }
    };
  }
}

