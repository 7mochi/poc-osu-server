package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "maps")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Beatmap {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long internalId;  // Clave primaria interna

  @Column(name = "beatmap_id", nullable = false, unique = true)
  private Integer beatmapId;  // beatmap difficulty ID from osu! API

  @Column(name = "beatmapset_id", nullable = false)
  private Integer beatmapsetId;  // beatmapset ID from osu! API

  @Column(nullable = false)
  private Integer status;

  @Column(nullable = false, length = 32, unique = true)
  private String md5;

  @Column(nullable = false)
  private String artist;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String creator;

  @Column(nullable = false)
  private String version;

  @Column(nullable = false)
  private Float cs;

  @Column(nullable = false)
  private Float ar;

  @Column(nullable = false)
  private Float od;

  @Column(nullable = false)
  private Float hp;

  @Column(nullable = false)
  private Float bpm;

  @Column(nullable = false)
  private Integer maxCombo;

  @Column(nullable = false)
  private Integer playcount;

  @Column(nullable = false)
  private Integer passcount;

  @Column(name = "last_update", nullable = false)
  private LocalDateTime lastUpdate;

  @Column(nullable = false, length = 50)
  private String server;
}
