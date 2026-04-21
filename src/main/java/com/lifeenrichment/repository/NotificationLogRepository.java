package com.lifeenrichment.repository;

import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationLog.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link NotificationLog}.
 *
 * <p>Used by {@code NotificationService} to persist delivery attempts and by
 * {@code NotificationScheduler} to find logs eligible for retry.
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /** Returns all logs for a user, most-recent-first. */
    List<NotificationLog> findByUserIdOrderBySentAtDesc(UUID userId);

    /**
     * Returns logs in the given status whose attempt count is below the cap —
     * used by the retry scheduler to find candidates for re-dispatch.
     */
    List<NotificationLog> findByStatusAndAttemptCountLessThan(DeliveryStatus status, int maxAttempts);

    /** Returns all logs associated with a specific activity, enrollment, or other entity. */
    List<NotificationLog> findByReferenceId(UUID referenceId);
}
