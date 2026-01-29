package pe.nanamochi.banchus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * Custom multipart resolver that bypasses Tomcat's file count limit.
 * 
 * The issue is that Tomcat 11 has a security limit on the number of multipart files
 * that can be uploaded in a single request. This limit is enforced at the
 * Request.parseParts() level before Spring can even see the request.
 */
@Configuration
public class CustomMultipartConfig {

  @Bean
  public MultipartResolver multipartResolver() {
    StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
    return resolver;
  }
}
