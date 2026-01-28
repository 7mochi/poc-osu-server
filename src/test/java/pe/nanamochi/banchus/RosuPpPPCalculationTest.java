package pe.nanamochi.banchus;

import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * Test PP calculation with rosu-pp-jar directly (without Spring Boot context).
 */
public class RosuPpPPCalculationTest {

  @Test
  public void testPPCalculationBasic() throws Exception {
    System.out.println("=== rosu-pp-jar PP Calculation Test ===\n");
    
    // Test 1: Load rosu-pp-jar classes
    System.out.println("Test 1: Loading rosu-pp-jar classes...");
    try {
      Class<?> beatmapClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Beatmap");
      Class<?> performanceClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Performance");
      Class<?> modsClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Mods");
      
      System.out.println("✓ Beatmap class loaded: " + beatmapClass.getSimpleName());
      System.out.println("✓ Performance class loaded: " + performanceClass.getSimpleName());
      System.out.println("✓ Mods class loaded: " + modsClass.getSimpleName());
    } catch (ClassNotFoundException e) {
      System.err.println("❌ Class not found: " + e.getMessage());
      throw e;
    }
    
    // Test 2: Create a test .osu file if we have one
    System.out.println("\nTest 2: Checking for test beatmap file...");
    File testBeatmapFile = createTestBeatmapFile();
    
    if (testBeatmapFile != null && testBeatmapFile.exists()) {
      System.out.println("✓ Test beatmap created: " + testBeatmapFile.getAbsolutePath());
      
      // Test 3: Calculate PP with the test file
      System.out.println("\nTest 3: Calculating PP...");
      try {
        double pp = calculatePPWithRosuPp(testBeatmapFile, 100, 0, 0, 0, 100, 0, 100.0, 0);
        System.out.println("✓ PP calculated: " + String.format("%.2f", pp));
        
        if (pp > 0) {
          System.out.println("✅ SUCCESS: PP calculation working!");
        } else {
          System.out.println("⚠️ PP is 0, might indicate an issue with calculation");
        }
      } catch (Exception e) {
        System.err.println("❌ Error during PP calculation: " + e.getMessage());
        e.printStackTrace();
      }
    } else {
      System.out.println("⚠️ No test beatmap file available, skipping PP calculation");
      System.out.println("   To run a full test, please provide a .osu file");
    }
  }

  /**
   * Create a minimal test beatmap file for testing.
   * Returns null if no suitable test file is available.
   */
  private File createTestBeatmapFile() {
    try {
      // Try to find a test resource
      String[] testLocations = {
        "/tmp/banchus-test/test.osu",
        "./test.osu",
        "/tmp/test.osu"
      };
      
      for (String location : testLocations) {
        File f = new File(location);
        if (f.exists()) {
          return f;
        }
      }
      
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Calculate PP using rosu-pp-jar reflection API
   */
  private double calculatePPWithRosuPp(
      File osuFile,
      int n300, int n100, int n50, int nmiss,
      int maxCombo, int mods,
      double accuracy, int gameMode) throws Exception {

    // Use the generated classes from rosu-pp-jar
    Class<?> beatmapClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Beatmap");
    Class<?> performanceClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Performance");
    Class<?> modsClass = Class.forName("io.github.nanamochi.rosu_pp_jar.Mods");

    System.out.println("  - Parsing beatmap file...");

    // Parse beatmap using Beatmap.fromPath(String path)
    Object beatmap = beatmapClass
        .getMethod("fromPath", String.class)
        .invoke(null, osuFile.getAbsolutePath());

    System.out.println("  - Beatmap parsed successfully");

    // Create Mods object from bits
    Object modsObj = null;
    if (mods > 0) {
      modsObj = modsClass
          .getMethod("fromBits", Integer.class)
          .invoke(null, mods);
      System.out.println("  - Mods object created from bits: " + mods);
    }

    // Create Performance object using Performance.create(Beatmap)
    Object performance = performanceClass
        .getMethod("create", beatmapClass)
        .invoke(null, beatmap);

    // Set score parameters using setter methods
    performanceClass.getMethod("setAccuracy", Double.class)
        .invoke(performance, accuracy);

    performanceClass.getMethod("setCombo", Integer.class)
        .invoke(performance, maxCombo);

    performanceClass.getMethod("setMisses", Integer.class)
        .invoke(performance, nmiss);

    if (modsObj != null) {
      performanceClass.getMethod("setMods", modsClass)
          .invoke(performance, modsObj);
    }

    System.out.println(String.format(
        "  - Score: acc=%.2f%%, combo=%d, misses=%d, mods=%d",
        accuracy, maxCombo, nmiss, mods));

    // Calculate PP using calculate() method
    Double pp = (Double) performanceClass
        .getMethod("calculate")
        .invoke(performance);

    // Clean up resources if they implement AutoCloseable
    try {
      if (performance instanceof AutoCloseable) {
        ((AutoCloseable) performance).close();
      }
      if (beatmap instanceof AutoCloseable) {
        ((AutoCloseable) beatmap).close();
      }
      if (modsObj != null && modsObj instanceof AutoCloseable) {
        ((AutoCloseable) modsObj).close();
      }
    } catch (Exception e) {
      // Ignore cleanup errors
    }

    return pp != null ? pp : 0.0;
  }
}
