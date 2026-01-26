package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Data
@Table(name = "channels")
@EntityListeners(AuditingEntityListener.class)
public class Channel {
  @Id
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private UUID id = UUID.randomUUID();

  @Column(nullable = false, length = 32, unique = true)
  private String name;

  @Column(nullable = false, length = 256)
  private String topic;

  @Column(nullable = false)
  private int readPrivileges = 1;

  @Column(nullable = false)
  private int writePrivileges = 2;

  @Column(nullable = false)
  private boolean autoJoin = false;
}
