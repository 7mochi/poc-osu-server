package pe.nanamochi.banchus.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.nanamochi.banchus.services.ReplayService;
import pe.nanamochi.banchus.services.ScoreService;

/**
 * Configuración explícita para registrar servlets.
 * Necesaria para asegurar que ScoreSubmissionServlet se registre correctamente.
 */
@Configuration
public class ServletConfiguration {

  @Bean
  public ScoreSubmissionServlet scoreSubmissionServlet(ScoreService scoreService, ReplayService replayService) {
    return new ScoreSubmissionServlet(scoreService, replayService);
  }

  @Bean
  public ServletRegistrationBean<ScoreSubmissionServlet> scoreSubmissionServletRegistration(
      ScoreSubmissionServlet servlet) {
    ServletRegistrationBean<ScoreSubmissionServlet> registration =
        new ServletRegistrationBean<>(servlet, "/bancho/web/osu-submit-modular-selector.php");
    registration.setName("scoreSubmissionServlet");
    registration.setLoadOnStartup(1);
    return registration;
  }
}
