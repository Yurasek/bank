package org.example;

import jakarta.persistence.*;
import lombok.*;
import org.example.enums.*;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "refresh_tokens")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "family", nullable = false)
    private UUID family;  // detects token reuse (rotation attack)

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    @Enumerated(EnumType.STRING)
    private RevokedReason revokedReason;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    // Constructors, getters, builders...

    public boolean isActive() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void revoke(RevokedReason reason) {
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }
}


