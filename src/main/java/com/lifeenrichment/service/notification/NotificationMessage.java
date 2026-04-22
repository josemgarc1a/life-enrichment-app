package com.lifeenrichment.service.notification;

import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Immutable value object carrying all data required by a {@link NotificationChannel} adapter
 * to deliver a single notification to a single recipient.
 *
 * <p>Instances are constructed by the notification service using the Lombok-generated builder
 * ({@code NotificationMessage.builder()...build()}) and are never modified after creation.
 * The object is intentionally channel-agnostic so that the same service code can populate it
 * regardless of which channel will ultimately be used — each adapter reads only the fields
 * relevant to its own protocol.
 *
 * <p>Field applicability by channel:
 * <table border="1">
 *   <tr><th>Field</th><th>EMAIL</th><th>SMS</th><th>PUSH</th></tr>
 *   <tr><td>toAddress</td><td>email address</td><td>E.164 phone</td><td>FCM device token</td></tr>
 *   <tr><td>subject</td><td>email subject</td><td>ignored</td><td>notification title</td></tr>
 *   <tr><td>body</td><td>template context</td><td>message text</td><td>notification body</td></tr>
 *   <tr><td>notificationType</td><td>template selector</td><td>ignored</td><td>ignored</td></tr>
 * </table>
 *
 * @see NotificationChannel
 * @see ChannelResult
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    /**
     * Delivery channel for which this message was prepared (EMAIL, SMS, or PUSH).
     *
     * <p>This value must be consistent with the adapter that is selected via
     * {@link NotificationChannel#supports(NotificationLog.Channel)}: the notification
     * service sets this field so that the adapter and downstream logging both agree
     * on the intended channel.
     */
    private NotificationLog.Channel channel;

    /**
     * Unique identifier of the {@code User} entity who is the intended recipient.
     *
     * <p>Used for correlation in logs and for persisting the {@code NotificationLog} record.
     * Not transmitted to the downstream provider.
     */
    private UUID recipientUserId;

    /**
     * Channel-specific destination address for the downstream provider:
     * <ul>
     *   <li>EMAIL — a valid RFC 5321 email address (e.g. {@code jane@example.com})</li>
     *   <li>SMS — an E.164 formatted phone number (e.g. {@code +15005550006})</li>
     *   <li>PUSH — a Firebase Cloud Messaging (FCM) device registration token</li>
     * </ul>
     */
    private String toAddress;

    /**
     * Short descriptive heading for the notification.
     *
     * <p>Used as the email subject line and as the push notification title.
     * Ignored by the SMS adapter (SMS body is plain text without a subject).
     */
    private String subject;

    /**
     * Primary textual content of the notification.
     *
     * <p>For EMAIL, this value is passed to the Thymeleaf template as the {@code body}
     * variable; the final rendered HTML may differ from this raw string.
     * For SMS and PUSH, this text is sent verbatim as the message body.
     */
    private String body;

    /**
     * Notification event classification, used by {@code EmailNotificationChannel} to resolve
     * the correct Thymeleaf template from the {@code resources/templates/notification/} directory.
     *
     * <p>The template name is derived by lower-casing the enum name and replacing underscores
     * with hyphens (e.g. {@code ACTIVITY_REMINDER} → {@code notification/activity-reminder}).
     * Ignored by SMS and PUSH adapters.
     */
    private NotificationPreference.NotificationType notificationType;
}
