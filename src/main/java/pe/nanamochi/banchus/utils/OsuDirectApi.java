package pe.nanamochi.banchus.utils;

import java.util.Set;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import pe.nanamochi.banchus.entities.DirectDisplayMode;

public class OsuDirectApi {
  private static final RestTemplate restTemplate = new RestTemplate();
  public static final String BASE_URL = "https://osu.direct/api";
  private static final Set<String> SPECIAL_QUERIES = Set.of("Newest", "Top Rated", "Most Played");

  private OsuDirectApi() {}

  public static String search(
      String query, int mode, DirectDisplayMode displayMode, int pageOffset) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(BASE_URL + "/v2/search")
            .queryParam("amount", 100)
            .queryParam("offset", pageOffset * 100);

    if (!SPECIAL_QUERIES.contains(query)) {
      builder.queryParam("q", query);
    }

    switch (query) {
      case "Newest" -> builder.queryParam("sort", "ranked_date:desc");
      case "Top Rated" -> builder.queryParam("sort", "favourite_count:desc");
      case "Most Played" -> builder.queryParam("sort", "beatmaps.playcount:desc");
    }

    // -1 for all modes
    if (mode != -1) {
      builder.queryParam("mode", mode);
    }

    if (displayMode != DirectDisplayMode.ALL) {
      builder.queryParam("status", DirectDisplayMode.convertToOsuApiStatus(displayMode));
    }

    builder.queryParam("osudirect", "true");

    try {
      return restTemplate.getForObject(builder.toUriString(), String.class);
    } catch (Exception e) {
      return null;
    }
  }
}
