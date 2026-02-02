package pe.nanamochi.banchus.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OsuDirectQuery {
  NEWEST("Newest", "ranked_date:desc"),
  TOP_RATED("Top Rated", "favourite_count:desc"),
  MOST_PLAYED("Most Played", "beatmaps.playcount:desc");

  private final String query;
  private final String sort;

  public static OsuDirectQuery fromQuery(String query) {
    if (query == null) return null;

    for (OsuDirectQuery q : values()) {
      if (q.query.equalsIgnoreCase(query.trim())) {
        return q;
      }
    }
    return null;
  }
}
