package pe.nanamochi.banchus.entities.db;

import jakarta.persistence.*;
import lombok.*;

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
  private int country;
  private int privileges;
}
