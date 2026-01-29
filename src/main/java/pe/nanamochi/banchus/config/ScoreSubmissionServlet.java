package pe.nanamochi.banchus.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.nanamochi.banchus.services.ReplayService;
import pe.nanamochi.banchus.services.ScoreService;
import pe.nanamochi.banchus.utils.ScoreDecryption;

/**
 * Servlet for handling osu! score submissions.
 * Processes encrypted score data and saves it to the database.
 * Handles multipart form data including score data and replay files.
 */
public class ScoreSubmissionServlet extends HttpServlet {

  private static final Logger logger = LoggerFactory.getLogger(ScoreSubmissionServlet.class);

  private final ScoreService scoreService;
  private final ReplayService replayService;

  public ScoreSubmissionServlet(ScoreService scoreService, ReplayService replayService) {
    this.scoreService = scoreService;
    this.replayService = replayService;
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    long requestStartTime = System.currentTimeMillis();
    logger.info("=".repeat(63));
    logger.info("📥 Score submission received\n  Content-Type: {}\n  Content-Length: {}", 
        request.getContentType(), request.getContentLength());

    try {
      String contentType = request.getContentType();
      String boundary = null;
      
      // Extract boundary from Content-Type header
      if (contentType != null && contentType.contains("boundary=")) {
        boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
        // Remove quotes if present
        if (boundary.startsWith("\"")) {
          boundary = boundary.substring(1, boundary.length() - 1);
        }
      }

      String scoreData = null;
      String clientHash = null;
      String iv = null;
      String osuVersion = null;
      String username = null;
      byte[] replayBytes = null;

      if (boundary != null) {
        logger.info("  Boundary: {}", boundary);
        // Parse multipart manually (using binary processing to preserve replay data integrity)
        Map<String, MultipartPart> parts = parseMultipart(request.getInputStream(), boundary);
        
        // According to bancho.py protocol:
        // "score" = encrypted score data (base64 text field) - THIS IS WHAT WE WANT
        // "score" (binary file) = replay data file - ALSO IMPORTANT
        // "s" = encrypted client hash (base64) - NOT what we want
        // "fs" = client hash (base64) - FOR DECRYPTION KEY
        MultipartPart scorePart = parts.get("score");
        if (scorePart != null) {
          scoreData = scorePart.getText();
        }
        
        // Get client hash from field "fs" (base64 encoded - used for key derivation)
        MultipartPart clientHashPart = parts.get("fs");
        if (clientHashPart != null) {
          clientHash = clientHashPart.getText();
        }
        
        MultipartPart ivPart = parts.get("iv");
        if (ivPart != null) {
          iv = ivPart.getText();
        }
        
        MultipartPart osuVersionPart = parts.get("osuver");
        if (osuVersionPart != null) {
          osuVersion = osuVersionPart.getText();
        }
        
        MultipartPart usernamePart = parts.get("u");
        if (usernamePart == null) {
          usernamePart = parts.get("username");
        }
        if (usernamePart != null) {
          username = usernamePart.getText();
        }
        
        // Get replay data if present (can be named "replay" or in "scoreReplayBinary")
        // Keep as raw bytes to preserve binary integrity
        MultipartPart replayPart = parts.get("replay");
        if (replayPart == null) {
          replayPart = parts.get("scoreReplayBinary");
        }
        if (replayPart != null) {
          replayBytes = replayPart.getBytes();
        }
        
        StringBuilder partsLog = new StringBuilder("Parsed multipart fields:");
        if (replayBytes != null) {
          partsLog.append("\n  ✓ Replay data: ").append(replayBytes.length).append(" bytes");
        }
        if (scoreData != null) {
          partsLog.append("\n  ✓ Score data: ").append(scoreData.length()).append(" chars");
        }
        if (clientHash != null) {
          partsLog.append("\n  ✓ Client hash: ").append(clientHash);
        }
        if (iv != null) {
          partsLog.append("\n  ✓ IV: ").append(iv);
        }
        if (osuVersion != null) {
          partsLog.append("\n  ✓ osu version: ").append(osuVersion);
        }
        if (username != null) {
          partsLog.append("\n  ✓ Username: ").append(username);
        }
        logger.info(partsLog.toString());
      }

      if (scoreData == null || scoreData.isEmpty()) {
        logger.error("❌ Missing score data");
        response.setStatus(400);
        response.getWriter().write("error: missing score data");
        return;
      }

      // Validate and clean up base64 score data
      logger.info("📊 Raw score data length: {} chars", scoreData.length());
      logger.info("📊 Score data preview (first 100 chars): {}", 
          scoreData.substring(0, Math.min(100, scoreData.length())));
      if (scoreData.length() > 100) {
        logger.info("📊 Score data preview (last 50 chars): {}", 
            scoreData.substring(scoreData.length() - 50));
      }
      
      scoreData = cleanBase64Data(scoreData);
      logger.info("📊 After cleanup: {} chars", scoreData.length());
      
      if (!isValidBase64(scoreData)) {
        logger.error("❌ Invalid base64 encoding in score data");
        logger.error("   Data: {}", scoreData.substring(0, Math.min(200, scoreData.length())));
        response.setStatus(400);
        response.getWriter().write("error: invalid base64 encoding");
        return;
      }

      logger.info("✓ Score data is valid base64");
      logger.info("🔐 Decrypting score data...");
      String decrypted = ScoreDecryption.decryptScoreData(scoreData, clientHash, iv, osuVersion);

      logger.info("✓ Decrypted successfully");
      logger.info("📋 Parsing score data...");

      String[] parsedData = ScoreDecryption.parseScoreData(decrypted);
      ScoreDecryption.ParsedScore parsedScore = ScoreDecryption.ParsedScore.fromFields(parsedData);
      
      if (parsedScore == null) {
        logger.error("❌ Failed to parse score data");
        response.setStatus(400);
        response.getWriter().write("error: invalid score data");
        return;
      }

      logger.info("✓ ParsedScore created successfully\n" +
          "  - maxCombo: {}\n" +
          "  - n300: {}\n" +
          "  - n100: {}\n" +
          "  - n50: {}\n" +
          "  - ngeki: {}\n" +
          "  - nkatu: {}\n" +
          "  - nmiss: {}\n" +
          "  - score: {}\n" +
          "  - mods: {}",
          parsedScore.maxCombo, parsedScore.n300, parsedScore.n100, 
          parsedScore.n50, parsedScore.ngeki, parsedScore.nkatu, 
          parsedScore.nmiss, parsedScore.score, parsedScore.mods);

      // Save score using service and get response with score ID
      long scoreProcessStartTime = System.currentTimeMillis();
      ScoreService.SaveScoreResult result = scoreService.saveScoreWithResult(parsedScore, username != null ? username : parsedScore.username);
      long scoreProcessDuration = System.currentTimeMillis() - scoreProcessStartTime;
      logger.info("⏱️  Score processing took: {} ms", scoreProcessDuration);

      // Save replay if present and score was saved
      if (replayBytes != null && replayBytes.length > 0 && result.scoreId != null) {
        long replayStartTime = System.currentTimeMillis();
        logger.info("💾 Saving replay for score ID: {}", result.scoreId);
        try {
          logger.info("   Replay data size: {} bytes", replayBytes.length);
          
          boolean replaySaved = replayService.saveReplay(result.scoreId, replayBytes);
          
          if (replaySaved) {
            long replayDuration = System.currentTimeMillis() - replayStartTime;
            logger.info("✓ Replay saved successfully: {} bytes (took {} ms)", replayBytes.length, replayDuration);
          } else {
            logger.warn("⚠ Failed to save replay");
          }
        } catch (Exception e) {
          logger.error("⚠ Error saving replay: {}", e.getMessage());
        }
      } else if (replayBytes == null) {
        logger.info("ℹ No replay data provided");
      }
      
      String clientResponse = result.response;

      long totalDuration = System.currentTimeMillis() - requestStartTime;
      logger.info("=".repeat(63));
      logger.info("✓✓✓ SCORE SAVED SUCCESSFULLY ✓✓✓");
      logger.info("⏱️  TOTAL REQUEST DURATION: {} ms", totalDuration);
      logger.info("=".repeat(63));

      response.setStatus(200);
      response.setContentType("application/octet-stream");
      // Use OutputStream for binary protocol data (not Writer for text)
      response.getOutputStream().write(clientResponse.getBytes(StandardCharsets.UTF_8));
      response.getOutputStream().flush();

    } catch (Exception e) {
      logger.error("❌ Error processing score submission: {}", e.getMessage(), e);
      response.setStatus(500);
      response.getWriter().write("error: " + e.getMessage());
    }
  }

  /**
   * Parse multipart form data manually without using request.getParts()
   * This avoids Tomcat's FileCountLimitExceededException
   * Uses binary processing to avoid corrupting binary data (like replay files)
   */
  private Map<String, MultipartPart> parseMultipart(InputStream inputStream, String boundary) throws IOException {
    Map<String, MultipartPart> parts = new HashMap<>();
    
    byte[] buffer = new byte[8192];
    int bytesRead;
    byte[] allBytes = new byte[0];
    
    // Read all bytes from input stream
    while ((bytesRead = inputStream.read(buffer)) != -1) {
      byte[] newBytes = new byte[allBytes.length + bytesRead];
      System.arraycopy(allBytes, 0, newBytes, 0, allBytes.length);
      System.arraycopy(buffer, 0, newBytes, allBytes.length, bytesRead);
      allBytes = newBytes;
    }
    
    logger.debug("📦 Full multipart content length: {} bytes", allBytes.length);
    
    // Work with raw bytes to avoid encoding issues with binary data
    String boundaryString = "--" + boundary;
    byte[] boundaryBytes = boundaryString.getBytes(StandardCharsets.US_ASCII);
    
    // Find all boundary positions
    int currentPos = 0;
    while (true) {
      // Find next boundary
      int boundaryPos = indexOf(allBytes, boundaryBytes, currentPos);
      if (boundaryPos == -1) break;
      
      // Get the part between current position and boundary
      if (boundaryPos > currentPos) {
        byte[] partBytes = new byte[boundaryPos - currentPos];
        System.arraycopy(allBytes, currentPos, partBytes, 0, boundaryPos - currentPos);
        
        // Parse this part
        parseSinglePart(partBytes, parts);
      }
      
      currentPos = boundaryPos + boundaryBytes.length;
    }
    
    logger.info("📦 Parsed {} fields from multipart request", parts.size());
    for (String key : parts.keySet()) {
      logger.info("   - {}: {} bytes", key, parts.get(key).getLength());
    }
    
    return parts;
  }

  /**
   * Find the index of a byte array within another byte array
   */
  private int indexOf(byte[] source, byte[] pattern, int fromIndex) {
    for (int i = fromIndex; i <= source.length - pattern.length; i++) {
      boolean found = true;
      for (int j = 0; j < pattern.length; j++) {
        if (source[i + j] != pattern[j]) {
          found = false;
          break;
        }
      }
      if (found) return i;
    }
    return -1;
  }

  /**
   * Parse a single multipart part (both headers and content)
   */
  private void parseSinglePart(byte[] partBytes, Map<String, MultipartPart> parts) throws IOException {
    // Find the header/content separator (double CRLF or double LF)
    int headerEndPos = -1;
    for (int i = 0; i < partBytes.length - 3; i++) {
      if ((partBytes[i] == '\r' && partBytes[i + 1] == '\n' && 
           partBytes[i + 2] == '\r' && partBytes[i + 3] == '\n') ||
          (partBytes[i] == '\n' && partBytes[i + 1] == '\n')) {
        headerEndPos = i;
        break;
      }
    }
    
    if (headerEndPos == -1) {
      logger.debug("⚠ Part has no header/content separator");
      return;
    }
    
    // Extract headers as string (headers are always ASCII/UTF-8)
    byte[] headerBytes = new byte[headerEndPos];
    System.arraycopy(partBytes, 0, headerBytes, 0, headerEndPos);
    String headers = new String(headerBytes, StandardCharsets.UTF_8);
    
    // Find where content starts (after the separator)
    int contentStartPos = headerEndPos;
    if (partBytes[contentStartPos] == '\r' && partBytes[contentStartPos + 1] == '\n') {
      contentStartPos += 2;
      if (partBytes[contentStartPos] == '\r' && partBytes[contentStartPos + 1] == '\n') {
        contentStartPos += 2;
      } else if (partBytes[contentStartPos] == '\n') {
        contentStartPos += 1;
      }
    } else if (partBytes[contentStartPos] == '\n') {
      contentStartPos += 1;
      if (contentStartPos < partBytes.length && partBytes[contentStartPos] == '\n') {
        contentStartPos += 1;
      }
    }
    
    // Extract content (as bytes)
    byte[] contentBytes = new byte[partBytes.length - contentStartPos];
    System.arraycopy(partBytes, contentStartPos, contentBytes, 0, contentBytes.length);
    
    // Remove trailing CRLF/LF if present
    int contentLength = contentBytes.length;
    if (contentLength >= 2 && contentBytes[contentLength - 2] == '\r' && contentBytes[contentLength - 1] == '\n') {
      contentLength -= 2;
    } else if (contentLength >= 1 && contentBytes[contentLength - 1] == '\n') {
      contentLength -= 1;
    }
    
    // Parse field name from headers
    String fieldName = null;
    boolean isTextField = !headers.toLowerCase().contains("filename=");
    
    for (String line : headers.split("\r?\n")) {
      if (line.toLowerCase().startsWith("content-disposition:")) {
        int nameStart = line.indexOf("name=\"") + 6;
        int nameEnd = line.indexOf("\"", nameStart);
        if (nameStart > 5 && nameEnd > nameStart) {
          fieldName = line.substring(nameStart, nameEnd);
          logger.debug("📦 Found field: {} (textField: {})", fieldName, isTextField);
        }
        break;
      }
    }
    
    if (fieldName == null) return;
    
    // Create MultipartPart with the content bytes
    // For text fields, trim the content after conversion to string
    byte[] finalContentBytes = new byte[contentLength];
    System.arraycopy(contentBytes, 0, finalContentBytes, 0, contentLength);
    
    // For text fields, convert to string, trim, and convert back to bytes
    if (isTextField) {
      String textContent = new String(finalContentBytes, StandardCharsets.UTF_8).trim();
      finalContentBytes = textContent.getBytes(StandardCharsets.UTF_8);
    }
    
    MultipartPart part = new MultipartPart(fieldName, finalContentBytes, isTextField);
    
    // Handle binary "score" field (this is the replay file)
    if (fieldName.equals("score") && !isTextField && !parts.containsKey("scoreReplayBinary")) {
      parts.put("scoreReplayBinary", part);
      logger.info("  ✓ Field '{}' (binary/replay): {} bytes, type: binary", 
          fieldName, contentLength);
    } else if (!parts.containsKey(fieldName)) {
      parts.put(fieldName, part);
      String preview = isTextField ? part.getText() : "[binary data]";
      if (preview.length() > 100) {
        preview = preview.substring(0, 100) + "...";
      }
      logger.info("  ✓ Field '{}': {} bytes, type: {}, preview: {}", 
          fieldName, contentLength, isTextField ? "text" : "binary", preview);
    } else {
      logger.debug("  ⚠ Skipping duplicate field: {} (keeping first {} bytes, ignoring {} bytes, type: {})", 
          fieldName, parts.get(fieldName).getLength(), contentLength, isTextField ? "text" : "binary");
    }
  }

  /**
   * Check if a string is valid base64 encoded data
   */
  private boolean isValidBase64(String data) {
    try {
      // Check for invalid characters in base64
      if (!data.matches("[A-Za-z0-9+/]*={0,2}")) {
        logger.error("   Contains invalid base64 characters");
        for (int i = 0; i < data.length(); i++) {
          char c = data.charAt(i);
          if (!isValidBase64Char(c)) {
            logger.error("   Invalid character at position {}: '{}' (code: {})", 
                i, c, (int)c);
            if (i > 0) {
              logger.error("   Context: ...{}{}{}", 
                  data.charAt(i - 1), c, 
                  i + 1 < data.length() ? data.charAt(i + 1) : "");
            }
            return false;
          }
        }
        return false;
      }
      
      // Try to decode
      Base64.getDecoder().decode(data);
      return true;
    } catch (IllegalArgumentException e) {
      logger.error("   Base64 decode error: {}", e.getMessage());
      return false;
    }
  }

  private boolean isValidBase64Char(char c) {
    return (c >= 'A' && c <= 'Z') || 
           (c >= 'a' && c <= 'z') || 
           (c >= '0' && c <= '9') || 
           c == '+' || c == '/' || c == '=';
  }

  /**
   * Clean up and decode base64 data
   */
  private String cleanBase64Data(String data) {
    // Remove any whitespace or line breaks
    data = data.replaceAll("\\s+", "");
    
    // Add padding if needed
    // Base64 strings must have length that's a multiple of 4
    int paddingNeeded = (4 - (data.length() % 4)) % 4;
    if (paddingNeeded > 0) {
      logger.debug("📊 Adding {} padding characters to base64", paddingNeeded);
      for (int i = 0; i < paddingNeeded; i++) {
        data += "=";
      }
    }
    
    return data;
  }
}
