package com.lifeenrichment.service;

import com.lifeenrichment.entity.ActivityEnrollment;
import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationLog.Channel;
import com.lifeenrichment.entity.NotificationLog.DeliveryStatus;
import com.lifeenrichment.entity.NotificationPreference;
import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import com.lifeenrichment.entity.Resident;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.repository.ActivityEnrollmentRepository;
import com.lifeenrichment.repository.NotificationLogRepository;
import com.lifeenrichment.repository.NotificationPreferenceRepository;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.service.notification.ChannelResult;
import com.lifeenrichment.service.notification.NotificationChannel;
import com.lifeenrichment.service.notification.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates notification delivery across all enabled channels for a given user and event type.
 *
 * <p>Public send methods are annotated with {@code @Async} so callers return immediately;
 * delivery happens on a Spring-managed thread pool. {@link #retryFailed()} is intentionally
 * synchronous because it is driven by the scheduler on its own thread.
 *
 * <p>Each dispatch attempt is recorded in {@link NotificationLog}. On success the status is
 * set to {@link DeliveryStatus#SENT}; on failure it is either left as {@link DeliveryStatus#RETRYING}
 * (if attempts remain) or promoted to {@link DeliveryStatus#FAILED} once
 * {@link #MAX_RETRY_ATTEMPTS} is reached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Maximum number of delivery attempts before a log entry is permanently failed. */
    static final int MAX_RETRY_ATTEMPTS = 3;

    private final List<NotificationChannel> channels;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ActivityEnrollmentRepository activityEnrollmentRepository;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Public async send methods
    // -------------------------------------------------------------------------

    /**
     * Sends an activity reminder to all residents enrolled in the given activity.
     *
     * @param activityId the ID of the activity for which the reminder is being sent
     * @param userId     unused in current routing — enrollments are resolved by activity ID
     */
    @Async
    @Transactional
    public void sendActivityReminder(UUID activityId, UUID userId) {
        List<ActivityEnrollment> enrollments = activityEnrollmentRepository.findByActivityId(activityId);
        for (ActivityEnrollment enrollment : enrollments) {
            Resident resident = enrollment.getResident();
            String name = resident.getFirstName();
            String activityTitle = enrollment.getActivity().getTitle();
            String subject = "Reminder: " + activityTitle + " starts soon";
            String body = "Hi " + name + ", " + activityTitle
                    + " is starting soon. We look forward to seeing you!";

            String toAddress = resolveToAddress(resident, Channel.EMAIL);
            dispatchToUser(getUserIdForResident(resident), toAddress,
                    NotificationType.ACTIVITY_REMINDER, subject, body, activityId);
        }
    }

    /**
     * Sends a cancellation notice to all residents enrolled in the given activity.
     *
     * @param activityId the ID of the cancelled activity
     */
    @Async
    @Transactional
    public void sendCancellationNotice(UUID activityId) {
        List<ActivityEnrollment> enrollments = activityEnrollmentRepository.findByActivityId(activityId);
        for (ActivityEnrollment enrollment : enrollments) {
            Resident resident = enrollment.getResident();
            String name = resident.getFirstName();
            String activityTitle = enrollment.getActivity().getTitle();
            String subject = activityTitle + " has been cancelled";
            String body = "We're sorry, " + activityTitle
                    + " has been cancelled. Please check with staff for details.";

            String toAddress = resolveToAddress(resident, Channel.EMAIL);
            dispatchToUser(getUserIdForResident(resident), toAddress,
                    NotificationType.ACTIVITY_CANCELLED, subject, body, activityId);
        }
    }

    /**
     * Sends an enrollment confirmation to the resident linked to the given enrollment.
     *
     * @param enrollmentId the ID of the {@link ActivityEnrollment}
     */
    @Async
    @Transactional
    public void sendEnrollmentConfirmation(UUID enrollmentId) {
        Optional<ActivityEnrollment> optEnrollment = activityEnrollmentRepository.findById(enrollmentId);
        if (optEnrollment.isEmpty()) {
            log.warn("sendEnrollmentConfirmation called with unknown enrollmentId={}", enrollmentId);
            return;
        }
        ActivityEnrollment enrollment = optEnrollment.get();
        Resident resident = enrollment.getResident();
        String name = resident.getFirstName();
        String activityTitle = enrollment.getActivity().getTitle();
        String date = enrollment.getActivity().getStartTime()
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a"));

        String subject = "You're enrolled in " + activityTitle;
        String body = "Hi " + name + ", you have been enrolled in " + activityTitle
                + " on " + date + ". See you there!";

        String toAddress = resolveToAddress(resident, Channel.EMAIL);
        dispatchToUser(getUserIdForResident(resident), toAddress,
                NotificationType.ENROLLMENT_CONFIRMED, subject, body, enrollmentId);
    }

    /**
     * Sends a broadcast message to a list of target users.
     *
     * @param message       the message body to deliver to each target user
     * @param targetUserIds the IDs of users who should receive the broadcast
     */
    @Async
    @Transactional
    public void sendBroadcast(String message, List<UUID> targetUserIds) {
        String subject = "Facility Announcement";
        for (UUID targetUserId : targetUserIds) {
            Optional<User> optUser = userRepository.findById(targetUserId);
            if (optUser.isEmpty()) {
                log.warn("sendBroadcast: user not found, userId={}", targetUserId);
                continue;
            }
            User user = optUser.get();
            String toAddress = user.getEmail();
            dispatchToUser(targetUserId, toAddress, NotificationType.BROADCAST, subject, message, null);
        }
    }

    // -------------------------------------------------------------------------
    // Synchronous retry (called by scheduler)
    // -------------------------------------------------------------------------

    /**
     * Retries all delivery logs that are in {@link DeliveryStatus#RETRYING} and have not yet
     * exhausted their attempt budget.
     *
     * <p>This method is intentionally <em>not</em> {@code @Async} — the scheduler drives it
     * on its own managed thread. On success the log status is set to {@link DeliveryStatus#SENT};
     * on continued failure the attempt count is incremented and, once {@link #MAX_RETRY_ATTEMPTS}
     * is reached, the status is promoted to {@link DeliveryStatus#FAILED} permanently.
     */
    @Transactional
    public void retryFailed() {
        List<NotificationLog> candidates =
                notificationLogRepository.findByStatusAndAttemptCountLessThan(
                        DeliveryStatus.RETRYING, MAX_RETRY_ATTEMPTS);

        log.info("retryFailed: found {} log(s) eligible for retry", candidates.size());

        for (NotificationLog logEntry : candidates) {
            NotificationChannel adapter = findChannel(logEntry.getChannel());
            if (adapter == null) {
                log.warn("retryFailed: no channel adapter for channel={}, logId={}",
                        logEntry.getChannel(), logEntry.getId());
                continue;
            }

            NotificationMessage message = NotificationMessage.builder()
                    .channel(logEntry.getChannel())
                    .recipientUserId(logEntry.getUser().getId())
                    .toAddress(logEntry.getUser().getEmail())
                    .subject(null)
                    .body(logEntry.getMessage())
                    .notificationType(logEntry.getNotificationType())
                    .build();

            ChannelResult result = adapter.send(message);
            if (result.isSuccess()) {
                logEntry.setStatus(DeliveryStatus.SENT);
                logEntry.setErrorMessage(null);
            } else {
                logEntry.setAttemptCount(logEntry.getAttemptCount() + 1);
                logEntry.setErrorMessage(result.getErrorMessage());
                if (logEntry.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
                    logEntry.setStatus(DeliveryStatus.FAILED);
                }
            }
            notificationLogRepository.save(logEntry);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Core dispatch method: checks user preferences, selects enabled channels, sends via
     * each channel adapter, and persists a {@link NotificationLog} entry per channel attempt.
     *
     * @param userId      the ID of the recipient user
     * @param toAddress   the primary delivery address (email or phone)
     * @param type        the notification event type used to look up channel preferences
     * @param subject     the notification subject or title
     * @param body        the notification body text
     * @param referenceId the UUID of the related domain entity (activity, enrollment, etc.); may be null
     */
    private void dispatchToUser(UUID userId, String toAddress, NotificationType type,
                                String subject, String body, UUID referenceId) {
        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            log.warn("dispatchToUser: user not found, userId={}", userId);
            return;
        }
        User user = optUser.get();

        NotificationPreference preference = notificationPreferenceRepository
                .findByUserIdAndNotificationType(userId, type)
                .orElse(null);

        boolean emailEnabled = preference == null || preference.isEmailEnabled();
        boolean smsEnabled   = preference != null && preference.isSmsEnabled();
        boolean pushEnabled  = preference != null && preference.isPushEnabled();

        if (emailEnabled) {
            sendViaChannel(user, Channel.EMAIL, user.getEmail(), subject, body, type, referenceId);
        }
        if (smsEnabled) {
            String smsAddress = resolvePhoneAddress(user, toAddress);
            sendViaChannel(user, Channel.SMS, smsAddress, subject, body, type, referenceId);
        }
        if (pushEnabled) {
            String pushAddress = user.getFcmToken();
            if (pushAddress == null || pushAddress.isBlank()) {
                log.warn("dispatchToUser: no FCM token for PUSH, userId={}", userId);
            } else {
                sendViaChannel(user, Channel.PUSH, pushAddress, subject, body, type, referenceId);
            }
        }
    }

    /**
     * Sends a notification via a single channel, records the attempt in a {@link NotificationLog},
     * and updates the log status based on the result.
     */
    private void sendViaChannel(User user, Channel channel, String toAddress,
                                String subject, String body, NotificationType type, UUID referenceId) {
        NotificationChannel adapter = findChannel(channel);
        if (adapter == null) {
            log.warn("sendViaChannel: no adapter registered for channel={}", channel);
            return;
        }

        NotificationLog logEntry = NotificationLog.builder()
                .user(user)
                .notificationType(type)
                .channel(channel)
                .status(DeliveryStatus.RETRYING)
                .referenceId(referenceId)
                .message(body)
                .attemptCount(0)
                .build();
        notificationLogRepository.save(logEntry);

        NotificationMessage message = NotificationMessage.builder()
                .channel(channel)
                .recipientUserId(user.getId())
                .toAddress(toAddress)
                .subject(subject)
                .body(body)
                .notificationType(type)
                .build();

        ChannelResult result = adapter.send(message);

        if (result.isSuccess()) {
            logEntry.setStatus(DeliveryStatus.SENT);
        } else {
            logEntry.setAttemptCount(logEntry.getAttemptCount() + 1);
            logEntry.setErrorMessage(result.getErrorMessage());
            if (logEntry.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
                logEntry.setStatus(DeliveryStatus.FAILED);
            }
            // otherwise leave as RETRYING so the retry scheduler can pick it up
        }
        notificationLogRepository.save(logEntry);
    }

    /**
     * Returns the first {@link NotificationChannel} bean that supports the given channel,
     * or {@code null} if none is registered.
     *
     * @param channel the delivery channel to find an adapter for
     * @return matching adapter, or {@code null}
     */
    private NotificationChannel findChannel(Channel channel) {
        return channels.stream()
                .filter(c -> c.supports(channel))
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves the primary phone address for SMS delivery. Neither {@link Resident} nor
     * {@link User} currently carries a phone field, so this method falls back to the
     * provided {@code fallbackAddress} (typically the email address) and logs a warning.
     *
     * @param user            the recipient user
     * @param fallbackAddress the address to use when no phone number is available
     * @return phone number, or {@code fallbackAddress} if none is stored
     */
    private String resolvePhoneAddress(User user, String fallbackAddress) {
        // Neither User nor Resident has a phone field in the current schema.
        // Fall back to the provided address and warn so this is easy to fix later.
        log.warn("resolvePhoneAddress: no phone field available for SMS, falling back to address={} for userId={}",
                fallbackAddress, user.getId());
        return fallbackAddress;
    }

    /**
     * Resolves the best to-address for the given channel by examining the resident.
     * Currently delegates to the email address for all channels since phone is not on the model.
     *
     * @param resident the resident to resolve an address for
     * @param channel  the delivery channel being targeted
     * @return the best available address for that channel
     */
    private String resolveToAddress(Resident resident, Channel channel) {
        // Resident does not have an email or phone field; address resolution must
        // go through the linked user account (resolved in dispatchToUser via getUserIdForResident).
        // Return null here — dispatchToUser will use user.getEmail() directly.
        return null;
    }

    /**
     * Looks up the {@link User} ID that corresponds to a {@link Resident}.
     *
     * <p>In this schema, residents do not have a direct {@code userId} foreign key.
     * The service uses the resident's ID as a surrogate reference and relies on
     * {@link #dispatchToUser} to load the user record. This method returns
     * {@code resident.getId()} as a placeholder — callers that need true user-level
     * dispatch should be updated once the resident-to-user link is formalised.
     *
     * @param resident the resident whose corresponding user ID is needed
     * @return the resident's own UUID (used as reference until the schema links residents to users)
     */
    private UUID getUserIdForResident(Resident resident) {
        // The Resident entity does not hold a userId FK in the current schema.
        // We return the resident's own ID so that dispatchToUser can attempt a
        // User lookup; if not found, a WARN is logged and dispatch is skipped cleanly.
        return resident.getId();
    }
}
