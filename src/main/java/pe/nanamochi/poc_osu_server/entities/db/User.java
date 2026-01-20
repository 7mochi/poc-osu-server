package pe.nanamochi.poc_osu_server.entities.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("3")
    private int id;
    private String username;
    private String email;
    private String passwordMd5;
    private int country;
    private int privileges;
}
