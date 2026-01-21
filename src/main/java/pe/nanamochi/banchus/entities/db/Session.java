package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id = UUID.randomUUID();
    @ManyToOne
    private User user;
    private int utcOffset;
    private int gamemode;
    private String country;
    private float latitude;
    private float longitude;
    private boolean displayCityLocation;
    private int action;
    private String infoText;
    private String beatmapMd5;
    private int beatmapId;
    private int mods;
    private boolean pmPrivate;
    private boolean receiveMatchUpdates;
    private UUID spectatorHostSessionId;
    private String awayMessage;
    private int multiplayerMatchId;
    private Instant lastCommunicatedAt;
    private int lastNpBeatmapId;
    private boolean isPrimarySession;
    private String osuVersion;
    private String osuPathMd5;
    private String adaptersStr;
    private String adaptersMd5;
    private String uninstallMd5;
    private String diskSignatureMd5;
}
