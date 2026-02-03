package pe.nanamochi.banchus.entities;

import lombok.Data;

@Data
public class ReplayFrame {
  private int buttonState;
  private int taikoByte;
  private float x;
  private float y;
  private int time;
}
