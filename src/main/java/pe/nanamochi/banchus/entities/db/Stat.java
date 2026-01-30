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
public class Stat implements Cloneable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @ManyToOne private User user;
  private Mode gamemode;
  private long totalScore;
  private long rankedScore;
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

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
