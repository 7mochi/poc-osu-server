package pe.nanamochi.banchus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.persistence.EntityListeners;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Beatmap {
  private Integer approved;

  @JsonProperty("submit_date")
  private Instant submitDate;

  @JsonProperty("approved_date")
  private Instant approvedDate;

  @JsonProperty("last_update")
  private Instant lastUpdate;

  private String artist;

  @JsonProperty("beatmap_id")
  private Integer beatmapId;

  @JsonProperty("beatmapset_id")
  private Integer beatmapsetId;

  private double bpm;
  private String creator;

  @JsonProperty("creator_id")
  private Integer creatorId;

  @JsonProperty("difficultyrating")
  private Double difficultyRating;

  @JsonProperty("diff_aim")
  private Double diffAim;

  @JsonProperty("diff_speed")
  private Double diffSpeed;

  @JsonProperty("diff_size")
  private Double diffSize;

  @JsonProperty("diff_overall")
  private Double diffOverall;

  @JsonProperty("diff_approach")
  private Double diffApproach;

  @JsonProperty("diff_drain")
  private Double diffDrain;

  @JsonProperty("hit_length")
  private Integer hitLength;

  private String source;

  @JsonProperty("genre_id")
  private Integer genreId;

  @JsonProperty("language_id")
  private Integer languageId;

  private String title;

  @JsonProperty("total_length")
  private Integer totalLength;

  private String version;

  @JsonProperty("file_md5")
  private String fileMd5;

  private Integer mode;
  private String tags;

  @JsonProperty("favourite_count")
  private Integer favouriteCount;

  private Double rating;
  private Integer playcount;
  private Integer passcount;

  @JsonProperty("count_normal")
  private Integer countNormal;

  @JsonProperty("count_slider")
  private Integer countSlider;

  @JsonProperty("count_spinner")
  private Integer countSpinner;

  @JsonProperty("max_combo")
  private Integer maxCombo;

  private Boolean storyboard;
  private Boolean video;

  @JsonProperty("download_unavailable")
  private Boolean downloadUnavailable;

  @JsonProperty("audio_unavailable")
  private Boolean audioUnavailable;

  @JsonProperty("submit_date")
  public void setSubmitDate(String submitDate) {
    this.submitDate =
        LocalDateTime.parse(submitDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .toInstant(ZoneOffset.UTC);
  }

  @JsonProperty("approved_date")
  @JsonSetter(nulls = Nulls.SKIP)
  public void setApprovedDate(String approvedDate) {
    this.approvedDate =
        LocalDateTime.parse(approvedDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .toInstant(ZoneOffset.UTC);
  }

  public void setLastUpdate(String lastUpdate) {
    this.lastUpdate =
        LocalDateTime.parse(lastUpdate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .toInstant(ZoneOffset.UTC);
  }

  @JsonProperty("storyboard")
  public void setStoryboard(String value) {
    this.storyboard = "1".equals(value);
  }

  @JsonProperty("video")
  public void setVideo(String value) {
    this.video = "1".equals(value);
  }

  @JsonProperty("download_unavailable")
  public void setDownloadUnavailable(String value) {
    this.downloadUnavailable = "1".equals(value);
  }

  @JsonProperty("audio_unavailable")
  public void setAudioUnavailable(String value) {
    this.audioUnavailable = "1".equals(value);
  }
}
