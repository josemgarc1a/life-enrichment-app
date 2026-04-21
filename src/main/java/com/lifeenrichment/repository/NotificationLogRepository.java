package com.lifeenrichment.repository;

import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationLog.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link NotificationLog}.
 *
 * <p>Used by {@code NotificationService} to persist every delivery attempt (one row per
 * channel per dispatch), and by {@code NotificationScheduler} to find logs eligible for
 * retry. Rows are append-only — status and attempt count are updated in place after
 * each dispatch attempt.
 *
 * <p>The retry window is controlled by {@code NotificationService#MAX_RETRY_ATTEMPTS} (3).
 * Logs with {@code attemptCount >= 3} and {@code status = FAILED} are permanently failed
 * and will not be returned by the retry query.
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Returns all delivery logs for a user ordered most-recent-first.
     * Used by the notification history endpoint and family-facing views.
     *
     * @param userId the ID of the user whose logs to retrieve
     * @return list of logs; empty if the user has no delivery history
     */
    List<NotificationLog> findByUserIdOrderBySentAtDesc(UUID userId);

    /**
     * Returns logs in the given delivery status whose attempt count is strictly below
     * {@code maxAttempts}. Used by the retry scheduler to find candidates for re-dispatch.
     *
     * <p>Typical call: {@code findByStatusAndAttemptCountLessThan(RETRYING, 3)} to find
     * all logs that have not yet exhausted their retry budget.
     *
     * @param status      the delivery status to filter on (usually {@code RETRYING} or {@code FAILED})
     * @param maxAttempts logs with {@code attemptCount >= maxAttempts} are excluded
     * @return list of retry candidates; empty if none qualify
     */
    List<NotificationLog> findByStatusAndAttemptCountLessThan(DeliveryStatus status, int maxAttempts);

    /**
     * Returns all logs whose {@code referenceId} matches the given ID.
     * The reference ID is the UUID of the related domain entity — typically an
     * activity ID or enrollment ID — allowing all notifications for a single event
     * to be retrieved together.
     *
     * @param referenceId the ID of the related activity, enrollment, or other entity
     * @return list of logs for that reference; empty if none have been sent for it
     */
    List<NotificationLog> findByReferenceId(UUID referenceId);
}
