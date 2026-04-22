package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO representing a single notification-channel preference record for a user.
 *
 * <p>Returned by the GET and PUT /preferences endpoints. Maps one-to-one from a
 * {@code NotificationPreference} entity; no sensitive data is exposed.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceResponse {

    /** Surrogate primary key of the preference record. */
    private UUID id;

    /** The notification event type this preference applies to. */
    private NotificationType notificationType;

    /** Whether email delivery is enabled for this notification type. */
    private boolean emailEnabled;

    /** Whether SMS delivery is enabled for this notification type. */
    private boolean smsEnabled;

    /** Whether push delivery is enabled for this notification type. */
    private boolean pushEnabled;
}
