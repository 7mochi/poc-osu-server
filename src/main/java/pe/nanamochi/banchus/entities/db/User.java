package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import pe.nanamochi.banchus.entities.CountryCode;
import pe.nanamochi.banchus.entities.ServerPrivileges;

@Entity
@Data
@Table(name = "users")
public class User {
  @Id
  @SequenceGenerator(name = "user_seq", initialValue = 3, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
  @Column(name = "id", nullable = false, unique = true)
  private int id;

  @Column(name = "username", length = 32, nullable = false, unique = true)
  private String username;

  @Column(name = "email", length = 64, nullable = false, unique = true)
  private String email;

  @Column(name = "password_md5", length = 32, nullable = false)
  private String passwordMd5;

  @Column(name = "country", length = 2, nullable = false)
  private CountryCode country;

  @Column(name = "silence_end")
  private Instant silenceEnd;

  @Column(name = "privileges", nullable = false)
  private int privileges = 1;

  public boolean isRestricted() {
    return !ServerPrivileges.fromBitmask(this.getPrivileges())
        .contains(ServerPrivileges.UNRESTRICTED);
  }
}
