package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores per-user notification channel preferences for each notification type.
 *
 * <p>One row per (user, notificationType) pair. Defaults: email on, SMS off, push off.
 * The service layer falls back to email-only when no preference record exists.
 */
@Entity
@Table(name = "notification_preferences",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_notification_preference_user_type",
               columnNames = {"user_id", "notification_type"}
       ),
       indexes = {
           @Index(name = "idx_notif_pref_user", columnList = "user_id")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Builder.Default
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Builder.Default
    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = false;

    @Builder.Default
    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Notification event types shared across preferences and logs. */
    public enum NotificationType {
        /** Activity is about to start — sent to enrolled residents and staff. */
        ACTIVITY_REMINDER,

        /** Activity was cancelled or rescheduled — sent to enrolled residents, staff, and family. */
        ACTIVITY_CANCELLED,

        /** Resident was enrolled in an activity — sent to the resident and family members. */
        ENROLLMENT_CONFIRMED,

        /**
         * Resident actually attended an activity (attendance logged as ATTENDED).
         * Primarily family-facing: lets relatives know their loved one participated.
         */
        ATTENDANCE_LOGGED,

        /**
         * A new photo from an activity the resident attended has been posted.
         * Family-facing hook for Epic 6 (Photo Sharing) — infrastructure ready now,
         * trigger wired when photo upload is implemented.
         */
        PHOTO_AVAILABLE,

        /**
         * Resident's participation rate has dropped below the configured threshold.
         * Sent to family members and directors to prompt follow-up.
         */
        LOW_PARTICIPATION_ALERT,

        /** Manual broadcast from a Director to all residents or a targeted group. */
        BROADCAST
    }
}
