package com.lifeenrichment.repository;

import com.lifeenrichment.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditLog} entries.
 *
 * <p>Provides derived queries for user-scoped and action-scoped lookups,
 * plus a custom JPQL query that supports the Director-facing audit dashboard.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Returns all audit entries for a specific user, newest first (by default sort). */
    Page<AuditLog> findByUser_Id(UUID userId, Pageable pageable);

    /** Returns all audit entries matching a specific action string (e.g. {@code "LOGIN_FAILED"}). */
    Page<AuditLog> findByAction(String action, Pageable pageable);

    /**
     * Flexible paginated query used by the audit dashboard.
     * {@code userId} is optional — pass {@code null} to include entries for all users.
     * The {@code from}/{@code to} bounds are always required (callers should default them).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.user.id = :userId)
              AND a.occurredAt BETWEEN :from AND :to
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditLog> findWithFilters(
            @Param("userId") UUID userId,
            @Param("from")   LocalDateTime from,
            @Param("to")     LocalDateTime to,
            Pageable pageable);
}
