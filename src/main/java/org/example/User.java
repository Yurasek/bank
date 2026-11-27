package org.example;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.example.enums.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue
    private UUID id;

// Login identifier — can be email, phone, or username
    @Column(unique = true)
    private String firstName;
    
    @Column(unique = true, name = "last_name")
    private String secondName;

    // NEVER expose this, NEVER join to other tables on this
     private String passwordHash;

// Account status
     @Enumerated(EnumType.STRING)
     private UserStatus status = UserStatus.ACTIVE;

     private int failedLoginAttempts = 0;
     private Instant lockedUntil;

// Link to business domain — but only this UUID
    @Column(unique = true)
     private UUID clientId;  // ← foreign reference, not primary key

    Instant createdAt = Instant.now();
    Instant lastLoginAt;


}
