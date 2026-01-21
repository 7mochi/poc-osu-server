package pe.nanamochi.banchus.packets.server;

import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
public class ChannelInfoCompletePacket implements Packet {
    @Override
    public Packets getPacketType() {
        return Packets.BANCHO_CHANNEL_INFO_COMPLETE;
    }
}
