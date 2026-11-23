package org.example;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.*;
import org.springframework.stereotype.*;

import java.time.*;
import java.util.*;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Find by hash (main lookup)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Find active tokens for user (for "logout all devices")
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    // Find by family (for rotation attack detection)
    List<RefreshToken> findByFamily(UUID family);

    // Cleanup expired/revoked tokens
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now OR t.revokedAt IS NOT NULL")
    int deleteExpiredAndRevoked(@Param("now") Instant now);

    // Count active tokens per user (rate limiting)
    long countByUserIdAndRevokedAtIsNull(UUID userId);
}
