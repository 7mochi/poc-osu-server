package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "scores")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Score {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "map_md5", nullable = true, length = 32)
  private String mapMd5;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "map_md5", referencedColumnName = "md5", insertable = false, updatable = false)
  private Beatmap beatmap;

  @Column(nullable = false)
  private Integer score;

  @Column(nullable = false)
  private Float pp;

  @Column(nullable = false)
  private Float acc;

  @Column(name = "max_combo", nullable = false)
  private Integer maxCombo;

  @Column(nullable = false)
  private Integer mods;

  @Column(nullable = false)
  private Integer n300;

  @Column(nullable = false)
  private Integer n100;

  @Column(nullable = false)
  private Integer n50;

  @Column(nullable = false)
  private Integer nmiss;

  @Column(nullable = false)
  private Integer ngeki;

  @Column(nullable = false)
  private Integer nkatu;

  @Column(nullable = false)
  private Boolean perfect;

  @Column(nullable = false)
  private Integer status;

  @Column(nullable = false)
  private Boolean passed;

  @Column(name = "play_time", nullable = false)
  private LocalDateTime playTime;

  @Column(name = "game_mode", nullable = false)
  private Integer gameMode;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "userid", nullable = false, referencedColumnName = "id")
  private User user;
}
