package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import pe.nanamochi.banchus.entities.CountryCode;

@Entity
@Data
@Table(name = "users")
public class User {
  @Id
  @SequenceGenerator(name = "user_seq", initialValue = 3, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
  private int id;

  private String username;
  private String email;
  private String passwordMd5;
  private CountryCode country;
  private boolean restricted;
  private Instant silenceEnd;
  private int privileges = 0;
}
