package pe.nanamochi.banchus.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import pe.nanamochi.banchus.entities.BeatmapDirectDisplayMode;
import pe.nanamochi.banchus.entities.OsuDirectQuery;

@Service
@RequiredArgsConstructor
public class OsuDirectApiService {
  private final RestTemplate restTemplate;

  public static final String BASE_URL = "https://osu.direct/api";

  public String search(
      String query, int mode, BeatmapDirectDisplayMode displayMode, int pageOffset) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(BASE_URL + "/v2/search")
            .queryParam("amount", 100)
            .queryParam("offset", pageOffset * 100);

    OsuDirectQuery osuDirectQuery = OsuDirectQuery.fromQuery(query);

    if (osuDirectQuery != null) {
      builder.queryParam("sort", osuDirectQuery.getSort());
    } else {
      builder.queryParam("q", query);
    }

    // -1 for all modes
    if (mode != -1) {
      builder.queryParam("mode", mode);
    }

    if (displayMode != BeatmapDirectDisplayMode.ALL) {
      builder.queryParam("status", BeatmapDirectDisplayMode.convertToOsuApiStatus(displayMode));
    }

    builder.queryParam("osudirect", "true");

    try {
      String result = restTemplate.getForObject(builder.toUriString(), String.class);

      String[] parts = result.split("\\R", 2);
      int length = Integer.parseInt(parts[0]);
      // if we get 100 matches, send 101 to inform the client there are more to get
      if (length == 100) {
        result = "101\n" + parts[1];
      }

      return result;
    } catch (Exception e) {
      return null;
    }
  }
}
