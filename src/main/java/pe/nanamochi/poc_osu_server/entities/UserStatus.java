package pe.nanamochi.poc_osu_server.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatus {
    private Status action;
    private String text;
    private List<Mods> mods;
    private Mode mode;
    private String beatmapChecksum;
    private int beatmapId;
    private boolean updateStats;
}
