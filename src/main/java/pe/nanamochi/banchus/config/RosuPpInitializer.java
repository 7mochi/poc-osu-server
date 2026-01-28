package pe.nanamochi.banchus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Initializes JNA and rosu-pp-jar at application startup.
 * This ensures native libraries and dependencies are properly loaded.
 */
@Component
@Configuration
public class RosuPpInitializer implements CommandLineRunner {

  private static final Logger logger = LoggerFactory.getLogger(RosuPpInitializer.class);

  @Override
  public void run(String... args) throws Exception {
    logger.info("🚀 [RosuPpInit] Spring Boot startup: Initializing rosu-pp-jar...");
    initializeRosuPp();
  }

  private void initializeRosuPp() {
    try {
      logger.info("[RosuPpInit] Step 1: Checking JNA availability...");
      
      // Try to load JNA Structure class
      try {
        Class<?> jnaStructure = Class.forName("com.sun.jna.Structure");
        logger.info("[RosuPpInit] ✓ JNA Structure found: {}", jnaStructure.getName());
      } catch (ClassNotFoundException e) {
        logger.error("[RosuPpInit] ❌ JNA Structure not found!");
        logger.error("[RosuPpInit]    Error: {}", e.getMessage());
        throw e;
      }

      logger.info("[RosuPpInit] Step 2: Checking rosu-pp-jar availability...");
      
      // Try to load rosu-pp-jar main classes
      Class<?> beatmapClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Beatmap");
      logger.info("[RosuPpInit] ✓ rosu-pp-jar Beatmap class found: {}", beatmapClass.getName());

      logger.info("[RosuPpInit] Step 3: Loading rosu-pp-jar helper classes...");
      
      Class<?> performanceClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Performance");
      logger.info("[RosuPpInit] ✓ Performance class found: {}", performanceClass.getName());
      
      Class<?> modsClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Mods");
      logger.info("[RosuPpInit] ✓ Mods class found: {}", modsClass.getName());

      logger.info("✅ [RosuPpInit] All rosu-pp-jar classes loaded successfully!");

      logger.info("[RosuPpInit] ✅ RosuPp initialization SUCCESSFUL");
      logger.info("[RosuPpInit] All components ready for PP calculation");

    } catch (Exception e) {
      logger.error("[RosuPpInit] ❌ FAILED to initialize rosu-pp-jar", e);
      logger.error("[RosuPpInit] Error: {}", e.getMessage());
      logger.error("[RosuPpInit] Class: {}", e.getClass().getName());
      
      // Print classpath info
      logger.error("[RosuPpInit] Java classpath: {}", System.getProperty("java.class.path"));
      logger.error("[RosuPpInit] Current classloader: {}", this.getClass().getClassLoader());
      
      e.printStackTrace();
    }
  }
}
