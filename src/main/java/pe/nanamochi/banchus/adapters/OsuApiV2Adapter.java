package pe.nanamochi.banchus.adapters;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.nanamochi.banchus.entities.db.Beatmap;

/**
 * Adapter to query osu! API v2 and retrieve beatmap information.
 * Note: Uses a simplified approach without Jackson to avoid dependency resolution issues.
 */
@Component
public class OsuApiV2Adapter {

  private static final Logger logger = LoggerFactory.getLogger(OsuApiV2Adapter.class);
  private static final String OSU_API_V2_URL = "https://osu.ppy.sh/api/v2";

  @Value("${osu.api.client-id:0}")
  private String clientId;

  @Value("${osu.api.client-secret:}")
  private String clientSecret;

  private String accessToken = null;
  private long tokenExpiry = 0;

  /**
   * Get osu! API access token
   */
  private String getAccessToken() {
    try {
      // If we have a valid token, reuse it
      if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
        logger.debug("✓ Using cached osu! API token");
        return accessToken;
      }

      logger.info("URL: https://osu.ppy.sh/oauth/token");
      logger.info("Method: POST");
      logger.info("Body Parameters:");
      logger.info("   - client_id: {}", clientId);
      logger.info("   - client_secret: {}", clientSecret);
      logger.info("   - grant_type: client_credentials");
      logger.info("   - scope: public");

      // Construir request de OAuth
      String tokenUrl = "https://osu.ppy.sh/oauth/token";
      String body = "client_id=" + clientId + "&client_secret=" + clientSecret
          + "&grant_type=client_credentials&scope=public";

      logger.info("Full POST Body: {}", body);

      HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .header("Accept", "application/json")
          .header("Content-Type", "application/x-www-form-urlencoded")
          .build();

      HttpClient client = HttpClient.newHttpClient();
      long startTime = System.currentTimeMillis();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      long elapsed = System.currentTimeMillis() - startTime;

      logger.info("� Response Status: {} ({}ms)", response.statusCode(), elapsed);

      if (response.statusCode() != 200) {
        logger.error("Error obteniendo token de osu! API: {} {}", response.statusCode(),
            response.body());
        return null;
      }

      // Parsear token de respuesta JSON de forma simple
      String responseBody = response.body();
      int accessTokenIdx = responseBody.indexOf("\"access_token\":\"");
      if (accessTokenIdx == -1) {
        logger.error("Token no encontrado en respuesta de osu! API");
        return null;
      }

      int startIdx = accessTokenIdx + 16;
      int endIdx = responseBody.indexOf("\"", startIdx);
      accessToken = responseBody.substring(startIdx, endIdx);

      // Parsear expiry
      int expiresIdx = responseBody.indexOf("\"expires_in\":");
      if (expiresIdx != -1) {
        int startNum = expiresIdx + 13;
        int endNum = responseBody.indexOf(",", startNum);
        if (endNum == -1) endNum = responseBody.indexOf("}", startNum);
        String expiresStr = responseBody.substring(startNum, endNum).trim();
        int expiresIn = Integer.parseInt(expiresStr);
        tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000L);
        logger.info("Token obtained successfully!");
        logger.info("   Token (first 20 chars): {}...", accessToken.substring(0, Math.min(20, accessToken.length())));
        logger.info("   Expires in: {} seconds", expiresIn);
      }

      return accessToken;

    } catch (Exception e) {
      logger.error("Exception to get access token", e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Buscar beatmap por MD5 hash
   */
  public Beatmap lookupBeatmapByMd5(String beatmapMd5) {
    try {
      String token = getAccessToken();
      if (token == null) {
        logger.warn("No token available para osu! API");
        return null;
      }

      String url = OSU_API_V2_URL + "/beatmaps/lookup?checksum=" + beatmapMd5;
      logger.info("🌐 OSU! API v2 - LOOKUP BEATMAP BY MD5");
      logger.info("📍 URL: {}", url);
      logger.info("� Authorization: Bearer {}", token.substring(0, Math.min(20, token.length())) + "...");
      logger.info("📝 Headers: Accept: application/json");
      logger.info("🎯 MD5: {}", beatmapMd5);

      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .GET()
          .header("Authorization", "Bearer " + token)
          .header("Accept", "application/json")
          .build();

      HttpClient client = HttpClient.newHttpClient();
      long startTime = System.currentTimeMillis();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      long elapsed = System.currentTimeMillis() - startTime;

      logger.info("� Response Status: {} ({}ms)", response.statusCode(), elapsed);

      if (response.statusCode() == 404) {
        logger.warn("❌ Beatmap NOT FOUND in osu! API for MD5: {}", beatmapMd5);
        return null;
      }

      if (response.statusCode() != 200) {
        logger.error("❌ Error in osu! API: {}", response.statusCode());
        logger.error("Response Body: {}", response.body());
        return null;
      }

      logger.info("✅ SUCCESS! Response Body (first 300 chars):");
      logger.info("{}", response.body().substring(0, Math.min(300, response.body().length())));

      return parseBeatmapFromApi(response.body());

    } catch (Exception e) {
      logger.error("❌ Exception looking up beatmap by MD5: {}", e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Buscar beatmap por ID
   */
  public Beatmap lookupBeatmapById(int beatmapId) {
    try {
      String token = getAccessToken();
      if (token == null) {
        logger.warn("No token available para osu! API");
        return null;
      }

      String url = OSU_API_V2_URL + "/beatmaps/" + beatmapId;
      logger.info("🌐 OSU! API v2 - LOOKUP BEATMAP BY ID");
      logger.info("📍 URL: {}", url);
      logger.info("� Authorization: Bearer {}", token.substring(0, Math.min(20, token.length())) + "...");
      logger.info("📝 Headers: Accept: application/json");
      logger.info("🎯 Beatmap ID: {}", beatmapId);

      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .GET()
          .header("Authorization", "Bearer " + token)
          .header("Accept", "application/json")
          .build();

      HttpClient client = HttpClient.newHttpClient();
      long startTime = System.currentTimeMillis();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      long elapsed = System.currentTimeMillis() - startTime;

      logger.info("� Response Status: {} ({}ms)", response.statusCode(), elapsed);

      if (response.statusCode() == 404) {
        logger.warn("❌ Beatmap NOT FOUND en osu! API para ID: {}", beatmapId);
        return null;
      }

      if (response.statusCode() != 200) {
        logger.error("❌ Error en osu! API: {}", response.statusCode());
        logger.error("Response Body: {}", response.body());
        return null;
      }

      logger.info("✅ SUCCESS! Response Body (first 300 chars):");
      logger.info("{}", response.body().substring(0, Math.min(300, response.body().length())));

      return parseBeatmapFromApi(response.body());

    } catch (Exception e) {
      logger.error("❌ Exception looking up beatmap by ID: {}", e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Parsing osu! API v2 response (JSON string) and create Beatmap object
   */
  private Beatmap parseBeatmapFromApi(String jsonResponse) {
    try {
      logger.info("📊 PARSING OSU! API RESPONSE");
      logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
      logger.info("Full JSON Response:\n{}", jsonResponse);
      logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
      
      Beatmap beatmap = new Beatmap();

      // Parsear campos de forma simple desde JSON
      int beatmapId = extractIntValue(jsonResponse, "\"id\":");
      int beatmapsetId = extractIntValue(jsonResponse, "\"beatmapset_id\":");
      String title = extractStringValue(jsonResponse, "\"title\":\"");
      String artist = extractStringValue(jsonResponse, "\"artist\":\"");
      String version = extractStringValue(jsonResponse, "\"version\":\"");
      String creator = extractStringValue(jsonResponse, "\"creator\":\"");
      String md5 = extractStringValue(jsonResponse, "\"checksum\":\"");
      double ar = extractDoubleValue(jsonResponse, "\"ar\":");
      double cs = extractDoubleValue(jsonResponse, "\"cs\":");
      double od = extractDoubleValue(jsonResponse, "\"accuracy\":");
      double hp = extractDoubleValue(jsonResponse, "\"drain\":");
      double bpm = extractDoubleValue(jsonResponse, "\"bpm\":");
      
      logger.info("📋 EXTRACTED VALUES:");
      logger.info("  beatmap_id: {}", beatmapId);
      logger.info("  beatmapset_id: {}", beatmapsetId);
      logger.info("  title: {}", title);
      logger.info("  artist: {}", artist);
      logger.info("  version: {}", version);
      logger.info("  creator: {}", creator);
      logger.info("  md5/checksum: {}", md5);
      logger.info("  ar: {}", ar);
      logger.info("  cs: {}", cs);
      logger.info("  od/accuracy: {}", od);
      logger.info("  hp/drain: {}", hp);
      logger.info("  bpm: {}", bpm);
      
      // Extract status as string and convert to numeric value
      // osu! API v2 returns: "status":"ranked", "status":"pending", etc.
      String statusString = extractStringValue(jsonResponse, "\"status\":\"");
      int rankedStatus = convertStatusStringToInt(statusString);
      logger.info("  status: {} (converted to: {})", statusString, rankedStatus);

      // Set fields in Beatmap object
      beatmap.setBeatmapId(beatmapId);
      beatmap.setBeatmapsetId(beatmapsetId);
      beatmap.setStatus(rankedStatus);
      beatmap.setMd5(md5);
      beatmap.setArtist(artist.isEmpty() ? "" : artist);
      beatmap.setTitle(title.isEmpty() ? "" : title);
      beatmap.setVersion(version.isEmpty() ? "" : version);
      beatmap.setCreator(creator.isEmpty() ? "" : creator);
      beatmap.setLastUpdate(LocalDateTime.now());
      // beatmap.setFilename(artist + " - " + title + " (" + creator + ") [" + version + "].osu");
      // beatmap.setTotalLength(totalLength);
      beatmap.setMaxCombo(0); // Not available in lookup
      // beatmap.setFrozen(0);
      // beatmap.setPlays(0);
      // beatmap.setPasses(0);
      // beatmap.setMode(0); // standard
      beatmap.setBpm(Math.round(bpm * 100.0f) * 0.01f);
      beatmap.setCs(Math.round(cs * 100.0f) * 0.01f);
      beatmap.setAr(Math.round(ar * 100.0f) * 0.01f);
      beatmap.setOd(Math.round(od * 100.0f) * 0.01f);
      beatmap.setHp(Math.round(hp * 100.0f) * 0.01f);
      // beatmap.setDiff(Math.round((cs + ar + od + hp) / 4.0f * 100.0f) * 0.01f); // Approximated
      beatmap.setPlaycount(0);
      beatmap.setPasscount(0);
      beatmap.setServer("bancho");

      logger.info("✓ Beatmap parseado de osu! API: {} - {} [{}]", artist, title, version);
      logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
      return beatmap;

    } catch (Exception e) {
      logger.error("❌ Error parseando respuesta de osu! API: {}", e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Extract integer value from JSON
   */
  private int extractIntValue(String json, String key) {
    int idx = json.indexOf(key);
    if (idx == -1) return 0;
    int start = idx + key.length();
    int end = json.indexOf(",", start);
    if (end == -1) end = json.indexOf("}", start);
    try {
      return Integer.parseInt(json.substring(start, end).trim());
    } catch (Exception e) {
      return 0;
    }
  }

  /**
   * Extract double value from JSON
   */
  private double extractDoubleValue(String json, String key) {
    int idx = json.indexOf(key);
    if (idx == -1) return 0.0;
    int start = idx + key.length();
    int end = json.indexOf(",", start);
    if (end == -1) end = json.indexOf("}", start);
    try {
      return Double.parseDouble(json.substring(start, end).trim());
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Extract string value from JSON
   */
  private String extractStringValue(String json, String key) {
    int idx = json.indexOf(key);
    if (idx == -1) return "";
    int start = idx + key.length();
    int end = json.indexOf("\"", start);
    if (end == -1) return "";
    try {
      return json.substring(start, end);
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * Convert osu! API v2 status string to numeric status value
   * osu! API v2 returns: "ranked", "approved", "loved", "qualified", "pending", "wip", "graveyard"
   * Maps to: 1, 2, 4, 3, 0, -1, -2
   */
  private int convertStatusStringToInt(String statusString) {
    if (statusString == null || statusString.isEmpty()) {
      return 0; // pending
    }
    
    switch (statusString.toLowerCase()) {
      case "ranked":
        return 1;
      case "approved":
        return 2;
      case "qualified":
        return 3;
      case "loved":
        return 4;
      case "pending":
        return 0;
      case "wip":
        return -1;
      case "graveyard":
        return -2;
      default:
        logger.warn("Unknown beatmap status: {}", statusString);
        return 0; // default to pending
    }
  }
}
