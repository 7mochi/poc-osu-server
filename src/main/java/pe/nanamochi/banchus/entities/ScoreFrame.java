package pe.nanamochi.banchus.entities;

import lombok.Data;

@Data
public class ScoreFrame {
  private int time;
  private int id;
  private int total300;
  private int total100;
  private int total50;
  private int totalGeki;
  private int totalKatu;
  private int totalMiss;
  private int totalScore;
  private int maxCombo;
  private int currentCombo;
  private boolean perfect;
  private int hp;
  private int tagByte;
  private boolean usingScoreV2;
  private float comboPortion;
  private float bonusPortion;
}
