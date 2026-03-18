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

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByUser_Id(UUID userId, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);

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
