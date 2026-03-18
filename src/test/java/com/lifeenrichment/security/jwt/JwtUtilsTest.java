package com.lifeenrichment.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    private static final String TEST_SECRET =
            "test-secret-key-for-unit-tests-only-must-be-256bits-long";
    private static final long EXPIRATION_MS = 3_600_000L;       // 1 hour
    private static final long REFRESH_EXPIRATION_MS = 86_400_000L; // 24 hours
    private static final String TEST_EMAIL = "director@example.com";
    private static final String TEST_ROLE  = "DIRECTOR";

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpirationMs", REFRESH_EXPIRATION_MS);
    }

    // ── generateAccessToken ───────────────────────────────────────────────────

    @Test
    void generateAccessToken_producesNonNullToken() {
        String token = jwtUtils.generateAccessToken(TEST_EMAIL, TEST_ROLE);
        assertThat(token).isNotBlank();
    }

    @Test
    void generateAccessToken_tokenIsValid() {
        String token = jwtUtils.generateAccessToken(TEST_EMAIL, TEST_ROLE);
        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    void generateAccessToken_emailExtractedCorrectly() {
        String token = jwtUtils.generateAccessToken(TEST_EMAIL, TEST_ROLE);
        assertThat(jwtUtils.getEmailFromToken(token)).isEqualTo(TEST_EMAIL);
    }

    // ── generateRefreshToken ──────────────────────────────────────────────────

    @Test
    void generateRefreshToken_producesValidToken() {
        String token = jwtUtils.generateRefreshToken(TEST_EMAIL);
        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    void generateRefreshToken_emailExtractedCorrectly() {
        String token = jwtUtils.generateRefreshToken(TEST_EMAIL);
        assertThat(jwtUtils.getEmailFromToken(token)).isEqualTo(TEST_EMAIL);
    }

    // ── validateToken — negative cases ────────────────────────────────────────

    @Test
    void validateToken_returnsFalse_forExpiredToken() {
        // Generate token that expired 1 ms ago
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1L);
        String expiredToken = jwtUtils.generateAccessToken(TEST_EMAIL, TEST_ROLE);

        assertThat(jwtUtils.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forTamperedSignature() {
        String token = jwtUtils.generateAccessToken(TEST_EMAIL, TEST_ROLE);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

        assertThat(jwtUtils.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forTokenSignedWithDifferentSecret() {
        // Sign with a different secret
        JwtUtils otherUtils = new JwtUtils();
        ReflectionTestUtils.setField(otherUtils, "jwtSecret",
                "completely-different-secret-key-for-testing-purposes-ok");
        ReflectionTestUtils.setField(otherUtils, "jwtExpirationMs", EXPIRATION_MS);
        ReflectionTestUtils.setField(otherUtils, "refreshExpirationMs", REFRESH_EXPIRATION_MS);

        String foreignToken = otherUtils.generateAccessToken(TEST_EMAIL, TEST_ROLE);

        assertThat(jwtUtils.validateToken(foreignToken)).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forBlankToken() {
        assertThat(jwtUtils.validateToken("")).isFalse();
    }
}
