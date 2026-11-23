package org.example;

import lombok.*;
import lombok.extern.slf4j.*;
import org.example.dto.*;
import org.example.enums.*;
import org.example.util.*;
import org.springframework.context.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.security.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher eventPublisher;

    private static final int MAX_ACTIVE_TOKENS_PER_USER = 5;
    private static final int TOKEN_BYTE_LENGTH = 32;

    /**
     * Hash raw token for storage. NEVER store raw token.
     */
    private String hashToken(String rawToken) {
        return DigestUtils.sha256Hex(rawToken);
    }

    /**
     * Generate cryptographically secure random token.
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ═════════════════════════════════════════════════════════════════
    // CREATE
    // ═════════════════════════════════════════════════════════════════

    /**
     * Create new refresh token for user on login.
     */
    @Transactional
    public String createRefreshToken(UUID userId, String ipAddress, String userAgent) {
        // Enforce max tokens per user
        long activeCount = refreshTokenRepository.countByUserIdAndRevokedAtIsNull(userId);
        if (activeCount >= MAX_ACTIVE_TOKENS_PER_USER) {
            log.info("User {} has {} active tokens, revoking oldest", userId, activeCount);
            revokeOldestToken(userId);
        }

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);
        UUID family = UUID.randomUUID();

        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setFamily(family);
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        token.setIpAddress(ipAddress);
        token.setUserAgent(truncateUserAgent(userAgent));

        refreshTokenRepository.save(token);

        log.debug("Created refresh token for user {}, family {}", userId, family);

        // Return RAW token to client — never stored, never seen again
        return rawToken;
    }

    // ═════════════════════════════════════════════════════════════════
    // ROTATE (consume old, issue new)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Rotate refresh token: verify old, revoke it, issue new pair.
     * Detects token reuse attacks via family tracking.
     */
    @Transactional
    public Dto.TokenPair rotate(String rawRefreshToken, String ipAddress, String userAgent) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> {
            log.warn("Refresh token not found in database");
            return new RuntimeException("Invalid refresh token");
        });

        // Check if already revoked — possible rotation attack!
        if (oldToken.getRevokedAt() != null) {
            log.warn("Token reuse detected! User: {}, Family: {}, Reason: {}",
            oldToken.getUserId(),
            oldToken.getFamily(),
            oldToken.getRevokedReason());

            // Revoke entire family — assume compromise
            revokeEntireFamily(oldToken.getFamily(), RevokedReason.SUSPICIOUS_ACTIVITY);

            // Publish security event for alerting
//            eventPublisher.publishEvent(new TokenReuseDetectedEvent(
//            oldToken.getUserId(),
//            oldToken.getFamily(),
//            ipAddress,
//            oldToken.getRevokedAt(),
//            Instant.now()
//            ));

            throw new RuntimeException(
            "Security violation detected. Please login again with your password."
            );
        }

        // Check expiration
        if (Instant.now().isAfter(oldToken.getExpiresAt())) {
            oldToken.revoke(RevokedReason.EXPIRED);
            log.debug("Refresh token expired for user {}", oldToken.getUserId());
            throw new RuntimeException("Refresh token expired");
        }

        // Verify JWT signature matches
        if (!jwtUtil.isRefreshTokenValid(rawRefreshToken)) {
            log.warn("Refresh token signature invalid");
            throw new RuntimeException("Invalid refresh token");
        }

        // Load and validate user still active
        User user = userRepository.findById(oldToken.getUserId())
        .orElseThrow(() -> {
            log.error("User {} not found for valid refresh token", oldToken.getUserId());
            return new RuntimeException(oldToken.getUserId().toString());
        });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("User {} status is {}, revoking all tokens", user.getId(), user.getStatus());
            revokeAllUserTokens(user.getId(), RevokedReason.ADMIN_ACTION);
            throw new RuntimeException("Account is not active");
        }

        // Revoke old token
        oldToken.revoke(RevokedReason.NEW_LOGIN);

        // Create new token (same family for tracking)
        String newRawToken = generateSecureToken();
        String newTokenHash = hashToken(newRawToken);

        RefreshToken newToken = new RefreshToken();
        newToken.setId(UUID.randomUUID());
        newToken.setUserId(user.getId());
        newToken.setTokenHash(newTokenHash);
        newToken.setFamily(oldToken.getFamily()); // SAME FAMILY!
        newToken.setIssuedAt(Instant.now());
        newToken.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        newToken.setIpAddress(ipAddress);
        newToken.setUserAgent(truncateUserAgent(userAgent));

        refreshTokenRepository.save(newToken);

        // Generate new access token
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
        user.getLogin(),
        "",List.of()
        );

        String accessToken = jwtUtil.generateAccessToken(
        userDetails,
        user.getClientId()
        );

        log.debug("Rotated token for user {}, family {}", user.getId(), oldToken.getFamily());

        return new Dto.TokenPair(accessToken, newRawToken);
    }

    // ═════════════════════════════════════════════════════════════════
    // REVOKE
    // ═════════════════════════════════════════════════════════════════

    /**
     * Revoke single token (logout this device).
     */
    @Transactional
    public void revokeToken(String rawToken) {
        String hash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(hash)
        .ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke(RevokedReason.LOGOUT);
                log.debug("Revoked token for user {}", token.getUserId());
            }
        });
    }

    /**
     * Revoke all tokens for user (logout everywhere, password change, etc).
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId, RevokedReason reason) {
        List<RefreshToken> activeTokens = refreshTokenRepository
        .findByUserIdAndRevokedAtIsNull(userId);

        activeTokens.forEach(token -> token.revoke(reason));

        log.info("Revoked {} tokens for user {}, reason: {}",
        activeTokens.size(), userId, reason);
    }

    /**
     * Revoke entire token family (rotation attack detected).
     */
    @Transactional
    public void revokeEntireFamily(UUID family, RevokedReason reason) {
        List<RefreshToken> familyTokens = refreshTokenRepository.findByFamily(family);
        int revokedCount = 0;

        for (RefreshToken token : familyTokens) {
            if (token.getRevokedAt() == null) {
                token.revoke(reason);
                revokedCount++;
            }
        }

        log.warn("Revoked entire family {} ({} tokens), reason: {}",
        family, revokedCount, reason);
    }

    /**
     * Revoke oldest token when limit exceeded.
     */
    @Transactional
    public void revokeOldestToken(UUID userId) {
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
        .min((a, b) -> a.getIssuedAt().compareTo(b.getIssuedAt()))
        .ifPresent(oldest -> {
            oldest.revoke(RevokedReason.NEW_LOGIN);
            log.debug("Revoked oldest token for user {}, issued at {}",
            userId, oldest.getIssuedAt());
        });
    }

    // ═════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════

    /**
     * Truncate user agent to prevent storage issues.
     */
    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return "unknown";
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}
