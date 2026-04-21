package com.lifeenrichment.repository;

import com.lifeenrichment.entity.NotificationPreference;
import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link NotificationPreference}.
 *
 * <p>Used by {@code NotificationService} to determine which channels are enabled for a user
 * before dispatching a notification. When no preference record exists for a given
 * (user, type) pair, the service falls back to email-only delivery.
 *
 * <p>The unique constraint {@code uq_notification_preference_user_type} on
 * {@code (user_id, notification_type)} means at most one row per user per type
 * will ever be returned by these queries.
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    /**
     * Returns all preference rows configured for the given user, one per
     * {@link NotificationType} they have explicitly set.
     *
     * @param userId the ID of the user whose preferences to retrieve
     * @return list of preferences; empty if the user has not configured any
     */
    List<NotificationPreference> findByUserId(UUID userId);

    /**
     * Returns the preference for a specific user and notification type, if one exists.
     * This is the primary lookup used before each dispatch — call this to determine
     * whether email, SMS, and/or push are enabled for the given event type.
     *
     * @param userId the ID of the user
     * @param type   the notification event type (e.g. {@code ACTIVITY_REMINDER})
     * @return the preference, or {@link Optional#empty()} if the user has not configured this type
     */
    Optional<NotificationPreference> findByUserIdAndNotificationType(UUID userId, NotificationType type);
}
