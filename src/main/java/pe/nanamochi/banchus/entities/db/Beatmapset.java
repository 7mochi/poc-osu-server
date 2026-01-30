package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.ToString;
import pe.nanamochi.banchus.entities.BeatmapRankedStatus;

@Entity
@Data
@ToString(exclude = "beatmaps")
@Table(name = "beatmapsets")
public class Beatmapset {
  @Id private int id;

  @Column(nullable = true)
  private String title;

  @Column(nullable = true)
  private String titleUnicode;

  @Column(nullable = true)
  private String artist;

  @Column(nullable = true)
  private String artistUnicode;

  @Column(nullable = true)
  private String source;

  @Column(nullable = true)
  private String sourceUnicode;

  @Column(nullable = true)
  private String creator;

  @Column(nullable = true)
  private String tags = "";

  private BeatmapRankedStatus submissionStatus;
  private boolean hasVideo;
  private boolean hasStoryboard;
  private Instant submissionDate;
  private Instant approvedDate;
  private Instant lastUpdated;
  private int totalPlaycount;
  private int languageId; // TODO: enum?
  private int genreId; // TODO: enum?

  @OneToMany(mappedBy = "beatmapset", cascade = CascadeType.ALL)
  private List<Beatmap> beatmaps;
}
