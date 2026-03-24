package com.lifeenrichment.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Utility component for creating, validating, and parsing JWT tokens.
 *
 * <p>Produces two token types:
 * <ul>
 *   <li><em>Access token</em> — short-lived, carries the user's email and role as claims.</li>
 *   <li><em>Refresh token</em> — longer-lived, carries only the subject (email) and is
 *       stored server-side so it can be revoked on logout.</li>
 * </ul>
 * Token expiry durations are driven by {@code jwt.expiration-ms} and
 * {@code jwt.refresh-expiration-ms} in application properties.
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generates a signed access token embedding the user's email (subject) and role claim.
     *
     * @param email the user's email address, used as the JWT subject
     * @param role  the user's role name (e.g. {@code "DIRECTOR"}), stored as a custom claim
     * @return a compact, signed JWT string
     */
    public String generateAccessToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generates a refresh token containing only the user's email as its subject.
     * This token is stored server-side and compared on refresh requests.
     *
     * @param email the user's email address
     * @return a compact, signed JWT string
     */
    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the email (JWT subject) from a token without re-validating signature.
     * Only call this after {@link #validateToken} has returned {@code true}.
     *
     * @param token a compact JWT string
     * @return the email stored as the subject claim
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Validates the token's signature and expiry.
     *
     * @param token a compact JWT string
     * @return {@code true} if the token is well-formed, correctly signed, and not expired
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
