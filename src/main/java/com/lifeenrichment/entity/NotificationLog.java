package com.lifeenrichment.entity;

import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable audit record of every notification dispatch attempt.
 *
 * <p>A row is written before the channel adapter is called; the status is updated to
 * {@code SENT} or {@code FAILED} after the adapter returns. {@code attemptCount} is
 * incremented on each retry. Permanently failed records (attemptCount ≥ MAX_RETRY_ATTEMPTS)
 * have status {@code FAILED} and a non-null {@code errorMessage}.
 */
@Entity
@Table(name = "notification_logs",
       indexes = {
           @Index(name = "idx_notif_log_user",      columnList = "user_id"),
           @Index(name = "idx_notif_log_status",    columnList = "status"),
           @Index(name = "idx_notif_log_reference", columnList = "reference_id"),
           @Index(name = "idx_notif_log_sent_at",   columnList = "sent_at")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.RETRYING;

    /** ID of the related entity (activity, enrollment, etc.) for correlation. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** Populated on delivery failure; null on success. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    /** Delivery channel used for this log entry. */
    public enum Channel {
        EMAIL, SMS, PUSH
    }

    /** Delivery outcome for this log entry. */
    public enum DeliveryStatus {
        SENT, FAILED, RETRYING
    }
}
