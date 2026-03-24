package com.lifeenrichment.repository;

import com.lifeenrichment.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PasswordResetToken} entities.
 *
 * <p>Supports token lookup by hash (used during redemption) and bulk deletion
 * of expired tokens for scheduled housekeeping.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Finds a token record by the SHA-256 hash of the raw token string.
     * Returns empty if the token was never issued or has already been purged.
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Bulk-deletes all tokens whose expiry timestamp is before {@code now}. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    void deleteExpired(LocalDateTime now);
}
