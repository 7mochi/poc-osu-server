package pe.nanamochi.banchus.packets.client;

import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
public class ChannelLeavePacket implements Packet {
    private String name;

    @Override
    public Packets getPacketType() {
        return Packets.OSU_CHANNEL_LEAVE;
    }
}
