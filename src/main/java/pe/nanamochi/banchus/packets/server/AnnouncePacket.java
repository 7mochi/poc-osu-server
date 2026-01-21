package pe.nanamochi.banchus.packets.server;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncePacket implements Packet {
    private String message;

    @Override
    public Packets getPacketType() {
        return Packets.BANCHO_ANNOUNCE;
    }
}
