package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.NotificationLog.Channel;
import com.lifeenrichment.entity.NotificationLog.DeliveryStatus;
import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO representing a single notification delivery log entry.
 *
 * <p>Returned by the GET /logs endpoint (DIRECTOR only). Exposes all audit fields needed
 * for delivery troubleshooting without exposing internal user PII beyond what the Director
 * already has access to.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogResponse {

    /** Surrogate primary key of the log entry. */
    private UUID id;

    /** The notification event type that triggered this dispatch. */
    private NotificationType notificationType;

    /** The delivery channel used for this dispatch attempt. */
    private Channel channel;

    /** The current delivery outcome. */
    private DeliveryStatus status;

    /** The message body that was sent (or attempted). */
    private String message;

    /** Error detail recorded on delivery failure; null on success. */
    private String errorMessage;

    /** Number of dispatch attempts made for this log entry. */
    private int attemptCount;

    /** Timestamp of when the first dispatch attempt was made. */
    private LocalDateTime sentAt;

    /** UUID of the related domain entity (activity, enrollment, etc.) for correlation; may be null. */
    private UUID referenceId;
}
