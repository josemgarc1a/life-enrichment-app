package com.lifeenrichment.service.notification;

import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Value object carrying all data needed by a channel adapter to deliver one notification.
 *
 * <p>Built by the notification service and passed to the selected {@link NotificationChannel}
 * implementation. Immutable after construction via the Lombok {@code @Builder}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    /**
     * Delivery channel to use (EMAIL, SMS, PUSH).
     * Must match the adapter selected via {@link NotificationChannel#supports(NotificationLog.Channel)}.
     */
    private NotificationLog.Channel channel;

    /** ID of the {@code User} who should receive this notification. */
    private UUID recipientUserId;

    /**
     * Channel-specific destination address:
     * email address for EMAIL, E.164 phone number for SMS, FCM device token for PUSH.
     */
    private String toAddress;

    /** Subject line (used by EMAIL; may be used as the push notification title). */
    private String subject;

    /** Primary message body or template context. */
    private String body;

    /** The notification event type, used by the email adapter to select the Thymeleaf template. */
    private NotificationPreference.NotificationType notificationType;
}
