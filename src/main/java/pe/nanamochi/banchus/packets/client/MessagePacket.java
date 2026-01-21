package pe.nanamochi.banchus.packets.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagePacket implements Packet {
  private String sender;
  private String content;
  private String target;
  private int senderId;

  @Override
  public Packets getPacketType() {
    return Packets.OSU_MESSAGE;
  }

  public boolean isDirectMessage() {
    return !this.target.startsWith("#");
  }
}
