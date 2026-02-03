package pe.nanamochi.banchus.entities;

import java.util.List;
import lombok.Data;

@Data
public class ReplayFrameBundle {
  private ReplayAction action;
  private List<ReplayFrame> frames;
  private ScoreFrame frame;
  private int extra;
  private int sequence;
}
