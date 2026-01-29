package pe.nanamochi.banchus.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartException;

/**
 * Filter to handle multipart request exceptions and provide better error messages.
 * 
 * Catches FileCountLimitExceededException and other multipart parsing errors.
 */
@Component
public class MultipartExceptionFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(MultipartExceptionFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(request, response);
    } catch (ServletException e) {
      if (e.getCause() instanceof MultipartException
          || e.getMessage().contains("FileCountLimitExceededException")) {
        logger.error(
            "Multipart request exceeded limits. Consider increasing server.servlet.multipart.max-parts configuration.",
            e);
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        httpResponse.setContentType("application/json");
        httpResponse.getWriter()
            .write(
                "{\"error\": \"Request contains too many file parts. Maximum allowed parts: 1000\"}");
      } else {
        throw e;
      }
    }
  }
}
