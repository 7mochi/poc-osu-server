package pe.nanamochi.banchus.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Collection;

/**
 * This filter intercepts requests to /bancho/web/osu-submit-modular-selector.php
 * and prevents Spring from trying to parse the multipart request,
 * allowing the custom servlet to handle it directly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScoreSubmissionFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(ScoreSubmissionFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws ServletException, java.io.IOException {
    
    if (!(request instanceof HttpServletRequest)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    String path = httpRequest.getRequestURI();
    String method = httpRequest.getMethod();
    String queryString = httpRequest.getQueryString() != null ? "?" + httpRequest.getQueryString() : "";
    
    // Log ALL requests to /web/*
    if (path.contains("/web/")) {
      if (httpRequest.getQueryString() != null && !httpRequest.getQueryString().isEmpty()) {
        String[] params = httpRequest.getQueryString().split("&");
        StringBuilder paramLog = new StringBuilder();
        if (params.length > 0) {
          for (String param : params) {
            paramLog.append("\n  Param: ");
            if (param.length() > 100) {
              paramLog.append(param, 0, 100).append("...");
            } else {
              paramLog.append(param);
            }
          }
        }
        logger.info("[{}] {} | Query: {} | UA: {}{}", 
            method, path, queryString, httpRequest.getHeader("User-Agent"), paramLog);
      } else {
        logger.info("[{}] {} | Query: {} | UA: {}", 
            method, path, queryString, httpRequest.getHeader("User-Agent"));
      }
    }
    
    // If request is for score submission endpoint, wrap it to prevent Spring from parsing multipart
    if (path.contains("osu-submit-modular-selector.php") && "POST".equals(method)) {
      logger.info("🔗 Routing score submission to servlet, disabling Spring multipart parsing");
      // Wrap the request to prevent Spring from calling getParts()
      HttpServletRequest wrappedRequest = new HttpServletRequestWrapper((HttpServletRequest) request) {
        @Override
        public Collection<Part> getParts() throws IOException, ServletException {
          // Don't allow Spring to call getParts() - this causes the multipart parsing error
          throw new ServletException("Multipart parsing disabled for this request");
        }
        
        @Override
        public Part getPart(String name) throws IOException, ServletException {
          throw new ServletException("Multipart parsing disabled for this request");
        }
      };
      chain.doFilter(wrappedRequest, response);
      return;
    }

    // Continue with filter chain
    chain.doFilter(request, response);
  }
}
