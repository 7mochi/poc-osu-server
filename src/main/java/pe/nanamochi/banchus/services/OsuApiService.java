package pe.nanamochi.banchus.services;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import pe.nanamochi.banchus.entities.osuapi.Beatmap;

@Service
@RequiredArgsConstructor
public class OsuApiService {
  private static final String BASE_URL = "https://osu.ppy.sh";

  private final RestTemplate restTemplate;

  @Value("${banchus.osu-api.v1.key}")
  private String apiKey;

  public byte[] getOsuFile(int beatmapId) {
    ResponseEntity<byte[]> response =
        restTemplate.getForEntity(BASE_URL + "/osu/" + beatmapId, byte[].class);

    return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
  }

  public Beatmap getBeatmap(String beatmapMd5) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(BASE_URL + "/api/get_beatmaps")
            .queryParam("h", beatmapMd5)
            .queryParam("k", apiKey);

    ResponseEntity<List<Beatmap>> response =
        restTemplate.exchange(
            builder.toUriString(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
    List<Beatmap> beatmaps = response.getBody();

    return !beatmaps.isEmpty() ? beatmaps.getFirst() : null;
  }

  public Beatmap getBeatmap(int beatmapId) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(BASE_URL + "/api/get_beatmaps")
            .queryParam("b", beatmapId)
            .queryParam("k", apiKey);

    ResponseEntity<List<Beatmap>> response =
        restTemplate.exchange(
            builder.toUriString(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
    List<Beatmap> beatmaps = response.getBody();

    return !beatmaps.isEmpty() ? beatmaps.getFirst() : null;
  }

  public List<Beatmap> getBeatmaps(int beatmapSetId) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(BASE_URL + "/api/get_beatmaps")
            .queryParam("s", beatmapSetId)
            .queryParam("k", apiKey);

    // System.out.println(builder.toUriString());
    ResponseEntity<List<Beatmap>> response =
        restTemplate.exchange(
            builder.toUriString(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

    return response.getBody();
  }
}
