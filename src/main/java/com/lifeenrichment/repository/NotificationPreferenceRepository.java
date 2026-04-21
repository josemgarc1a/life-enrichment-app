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
 * <p>Used by {@code NotificationService} to determine which channels to use
 * before dispatching a notification.
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    /** Returns all preference rows for a user, one per notification type they have configured. */
    List<NotificationPreference> findByUserId(UUID userId);

    /** Returns the preference for a specific user + type, if one exists. */
    Optional<NotificationPreference> findByUserIdAndNotificationType(UUID userId, NotificationType type);
}
