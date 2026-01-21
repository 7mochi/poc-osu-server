package pe.nanamochi.banchus.packets.server;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPresencePacket implements Packet {
    private int userId;
    private String username;
    private int utcOffset;
    private int countryCode;
    private int permissions;
    private float latitude;
    private float longitude;
    private int globalRank;

    @Override
    public Packets getPacketType() {
        return Packets.BANCHO_USER_PRESENCE;
    }
}
