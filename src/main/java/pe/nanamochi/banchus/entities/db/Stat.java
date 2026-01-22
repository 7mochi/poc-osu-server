package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.entities.Mode;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Stat {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @ManyToOne private User user;
  private Mode gamemode;
  private int totalScore;
  private int rankedScore;
  private int performancePoints;
  private int playCount;
  private int playTime;
  private float accuracy;
  private int highestCombo;
  private int totalHits;
  private int replayViews;
  private int xhCount;
  private int xCount;
  private int shCount;
  private int sCount;
  private int aCount;
}
