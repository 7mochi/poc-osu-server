package pe.nanamochi.banchus.packets.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.nanamochi.banchus.entities.Mode;
import pe.nanamochi.banchus.entities.Mods;
import pe.nanamochi.banchus.entities.Status;
import pe.nanamochi.banchus.packets.Packet;
import pe.nanamochi.banchus.packets.Packets;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusPacket implements Packet {
    private Status action;
    private String text;
    private List<Mods> mods;
    private Mode mode;
    private String beatmapChecksum;
    private int beatmapId;

    @Override
    public Packets getPacketType() {
        return Packets.OSU_USER_STATUS;
    }
}
