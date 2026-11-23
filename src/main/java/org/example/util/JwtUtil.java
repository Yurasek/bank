package org.example.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtil(
    @Value("${jwt.secret}") String secret,
    @Value("${jwt.access-expiration:900000}") long accessTokenExpirationMs,  // 15 min
    @Value("${jwt.refresh-expiration:604800000}") long refreshTokenExpirationMs  // 7 days
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(UserDetails userDetails, UUID clientId) {
        Instant now = Instant.now();
        return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim("clientId", clientId.toString())
        .claim("type", "ACCESS")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(accessTokenExpirationMs)))
        .signWith(secretKey)
        .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Instant now = Instant.now();
        return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim("type", "REFRESH")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(refreshTokenExpirationMs)))
        .signWith(secretKey)
        .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return "ACCESS".equals(claims.get("type")) &&
            claims.getExpiration().after(Date.from(Instant.now()));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }

    public UUID extractClientId(String token) {
        return UUID.fromString((String) parseToken(token).get("clientId"));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return (List<String>) parseToken(token).get("roles");
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = parseToken(token);

            boolean isRefreshType = "REFRESH".equals(claims.get("type"));
            boolean notExpired = claims.getExpiration().after(Date.from(Instant.now()));

            return isRefreshType && notExpired;

        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
