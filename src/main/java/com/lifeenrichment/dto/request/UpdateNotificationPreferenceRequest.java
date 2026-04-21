package com.lifeenrichment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for upserting a single notification-type preference for the calling user.
 *
 * <p>All three channel flags are required in the request body. The controller maps these
 * directly onto the {@code NotificationPreference} entity, creating a new row if none exists
 * or updating the existing one if it does.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationPreferenceRequest {

    /** Whether the user wants to receive this notification type by email. */
    private boolean emailEnabled;

    /** Whether the user wants to receive this notification type by SMS. */
    private boolean smsEnabled;

    /** Whether the user wants to receive this notification type as a push notification. */
    private boolean pushEnabled;
}
