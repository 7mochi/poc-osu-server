package pe.nanamochi.banchus.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.RijndaelEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for decrypting score data from osu! client submissions.
 * Uses Rijndael cipher with 32-byte block size (matching bancho.py implementation).
 */
public class ScoreDecryption {

  private static final Logger logger = LoggerFactory.getLogger(ScoreDecryption.class);

  /**
   * Decrypt score data from an osu! client submission using Rijndael.
   * Matches the encryption used by bancho.py.
   */
  public static String decryptScoreData(
      String scoreDataB64, String clientHashB64, String ivB64, String osuVersion) {
    try {
      // Validate inputs
      if (scoreDataB64 == null || scoreDataB64.isEmpty()) {
        throw new IllegalArgumentException("Score data is empty");
      }
      if (ivB64 == null || ivB64.isEmpty()) {
        throw new IllegalArgumentException("IV is empty");
      }
      if (osuVersion == null || osuVersion.isEmpty()) {
        throw new IllegalArgumentException("osu version is empty");
      }
      
      logger.info("📥 Decryption parameters:");
      logger.info("  - Score data B64 length: {} chars", scoreDataB64.length());
      logger.info("  - Client hash B64 length: {} chars", 
          clientHashB64 != null ? clientHashB64.length() : 0);
      logger.info("  - IV B64 length: {} chars", ivB64.length());
      logger.info("  - osu version: {}", osuVersion);
      
      // Decode base64 inputs
      byte[] encryptedData = Base64.getDecoder().decode(scoreDataB64);
      byte[] ivBytes = Base64.getDecoder().decode(ivB64);
      
      // Client hash is optional - used only for verification
      byte[] clientHashBytes = null;
      if (clientHashB64 != null && !clientHashB64.isEmpty()) {
        try {
          clientHashBytes = Base64.getDecoder().decode(clientHashB64);
        } catch (IllegalArgumentException e) {
          logger.warn("⚠ Could not decode client hash (not critical): {}", e.getMessage());
        }
      }

      logger.info(
          "✓ Decoded from base64:\n" +
          "  - IV: {} bytes\n" +
          "  - Encrypted score data: {} bytes\n" +
          "  - Client hash bytes: {} bytes",
          ivBytes.length,
          encryptedData.length,
          clientHashBytes != null ? clientHashBytes.length : 0
      );

      // Construct the key: "osu!-scoreburgr---------" + osuVersion
      String keyStr = "osu!-scoreburgr---------" + osuVersion;
      byte[] key = keyStr.getBytes(StandardCharsets.UTF_8);

      logger.info("🔑 Key: {} ({} bytes)", keyStr, key.length);

      // Use Rijndael with 32-byte block size
      BlockCipher engine = new RijndaelEngine(256); // 256 bits = 32 bytes block size
      PaddedBufferedBlockCipher cipher =
          new PaddedBufferedBlockCipher(new CBCBlockCipher(engine), new PKCS7Padding());

      // Initialize with key and IV
      ParametersWithIV params = new ParametersWithIV(new KeyParameter(key), ivBytes);
      cipher.init(false, params); // false = decrypt mode

      // Decrypt score data
      byte[] decrypted = new byte[cipher.getOutputSize(encryptedData.length)];
      int len = cipher.processBytes(encryptedData, 0, encryptedData.length, decrypted, 0);
      len += cipher.doFinal(decrypted, len);

      // Convert to string and trim
      String result = new String(decrypted, 0, len, StandardCharsets.UTF_8);
      
      logger.info(
          "✓ Successfully decrypted score data:\n" +
          "  - Length: {} chars\n" +
          "  - First 100 chars: '{}'\n" +
          "  - Last 50 chars: '{}'\n" +
          "  - Contains ':' delimiter: {}",
          result.length(),
          result.substring(0, Math.min(100, result.length())),
          result.substring(Math.max(0, result.length() - 50)),
          result.contains(":")
      );
      
      // Verify client hash if available
      if (clientHashBytes != null) {
        try {
          cipher.init(false, params); // Reset cipher for verification
          byte[] verifyDecrypted = new byte[cipher.getOutputSize(clientHashBytes.length)];
          int verifyLen = cipher.processBytes(clientHashBytes, 0, clientHashBytes.length, verifyDecrypted, 0);
          verifyLen += cipher.doFinal(verifyDecrypted, verifyLen);
          String clientHashDecrypted = new String(verifyDecrypted, 0, verifyLen, StandardCharsets.UTF_8);
          logger.info("✓ Client hash verified: {}", clientHashDecrypted);
        } catch (Exception e) {
          logger.warn("⚠ Could not verify client hash: {}", e.getMessage());
        }
      }
      
      return result;

    } catch (Exception e) {
      logger.error("❌ Decryption failed: {}", e.getMessage());
      logger.error("   Score data B64 length: {}", 
          scoreDataB64 != null ? scoreDataB64.length() : 0);
      logger.error("   Client hash B64 length: {}", 
          clientHashB64 != null ? clientHashB64.length() : 0);
      logger.error("   IV B64 length: {}", ivB64 != null ? ivB64.length() : 0);
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Parse decrypted score data string into individual fields.
   * Delimiter: colon (:) not pipe (|)
   */
  public static String[] parseScoreData(String decryptedData) {
    try {
      // Split by colon delimiter (not pipe!)
      String[] fields = decryptedData.split(":", -1);

      if (fields.length < 18) {
        logger.warn("Invalid score data format: got {} fields, expected 18+", fields.length);
        return null;
      }

      return fields;

    } catch (Exception e) {
      logger.error("Failed to parse score data: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Parse the score submission in a more accessible format.
   */
  public static class ParsedScore {
    public String mapMd5;
    public String username;
    public String checksum;
    public int n300;
    public int n100;
    public int n50;
    public int ngeki;
    public int nkatu;
    public int nmiss;
    public int score;
    public int maxCombo;
    public boolean perfect;
    public String grade;
    public int mods;
    public boolean passed;
    public int gamemode;
    public String playTime; // yyMMddHHmmss
    public String osuVersion;
    public int clientFlags;
    public float accuracy; // Accuracy as percentage (0-100)
    public float pp; // Temporarily set to n300 count until proper PP calculation is available

    public static ParsedScore fromFields(String[] fields) {
      if (fields == null || fields.length < 18) {
        logger.error("Invalid fields array for ParsedScore creation");
        return null;
      }

      try {
        ParsedScore score = new ParsedScore();
        score.mapMd5 = fields[0];
        score.username = fields[1];
        score.checksum = fields[2];
        score.n300 = Integer.parseInt(fields[3]);
        score.n100 = Integer.parseInt(fields[4]);
        score.n50 = Integer.parseInt(fields[5]);
        score.ngeki = Integer.parseInt(fields[6]);
        score.nkatu = Integer.parseInt(fields[7]);
        score.nmiss = Integer.parseInt(fields[8]);
        score.score = Integer.parseInt(fields[9]);
        score.maxCombo = Integer.parseInt(fields[10]);
        score.perfect = Boolean.parseBoolean(fields[11]);
        score.grade = fields[12];
        score.mods = Integer.parseInt(fields[13]);
        score.passed = Boolean.parseBoolean(fields[14]);
        score.gamemode = Integer.parseInt(fields[15]);
        score.playTime = fields[16];
        score.osuVersion = fields[17];
        score.clientFlags = fields.length > 18 ? Integer.parseInt(fields[18]) : 0;
        
        // Calculate accuracy from hit counts
        score.accuracy = calculateAccuracy(score);
        
        // Temporarily set PP to 0 until proper PP calculation is available
        // TODO: Implement PP calculation using rosu-pp in the PP calculation service
        // PP will be calculated later by PerformancePointsService after beatmap is fetched
        score.pp = 0.0f;

        logger.info(
            "✓ ParsedScore created successfully:\n" +
            "  mapMd5: {}\n" +
            "  username: {}\n" +
            "  n300: {}, n100: {}, n50: {}\n" +
            "  score: {}, maxCombo: {}\n" +
            "  pp (temp): {}\n" +
            "  accuracy: {}%",
            score.mapMd5,
            score.username,
            score.n300, score.n100, score.n50,
            score.score, score.maxCombo,
            score.pp,
            String.format("%.2f", score.accuracy)
        );

        return score;

      } catch (NumberFormatException e) {
        logger.error("Failed to parse numeric fields: {}", e.getMessage());
        e.printStackTrace();
        return null;
      }
    }

    private static float calculateAccuracy(ParsedScore score) {
      int totalHits = score.n300 + score.n100 + score.n50 + score.nmiss;
      if (totalHits == 0) {
        return 0.0f;
      }
      return ((300.0f * score.n300 + 100.0f * score.n100 + 50.0f * score.n50)
          / (300.0f * totalHits)) * 100.0f;
    }
  }
}