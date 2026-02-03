package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Data
@Table(
    name = "channels",
    indexes = {@Index(name = "channels_name_idx", columnList = "name")})
@EntityListeners(AuditingEntityListener.class)
public class Channel {
  @Id
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @UuidGenerator
  @Column(name = "id", nullable = false, length = 36, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 96, unique = true)
  private String name;

  @Column(name = "topic", nullable = false, length = 256)
  private String topic;

  @Column(name = "read_privileges", nullable = false)
  private int readPrivileges;

  @Column(name = "write_privileges", nullable = false)
  private int writePrivileges;

  @Column(name = "auto_join", nullable = false)
  private boolean autoJoin;

  @Column(name = "temporary", nullable = false)
  private boolean temporary;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
