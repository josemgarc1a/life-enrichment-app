package com.lifeenrichment.service;

import com.lifeenrichment.entity.Activity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    // -----------------------------------------------------------------------
    // Mocks
    // -----------------------------------------------------------------------
    @Mock private NotificationChannel emailChannel;
    @Mock private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private ActivityEnrollmentRepository activityEnrollmentRepository;
    @Mock private UserRepository userRepository;

    private NotificationService notificationService;

    // -----------------------------------------------------------------------
    // Shared test data
    // -----------------------------------------------------------------------
    private UUID activityId;
    private UUID enrollmentId;
    private UUID userId;

    private User user;
    private Resident resident;
    private Activity activity;
    private ActivityEnrollment enrollment;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 21, 10, 0);

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        // Wire the service manually so we can inject a List<NotificationChannel>
        notificationService = new NotificationService(
                List.of(emailChannel),
                notificationPreferenceRepository,
                notificationLogRepository,
                activityEnrollmentRepository,
                userRepository
        );

        activityId   = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        userId       = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("resident@example.com")
                .passwordHash("hash")
                .role(User.Role.FAMILY_MEMBER)
                .build();

        resident = Resident.builder()
                .id(userId) // resident ID == userId (surrogate, as per service logic)
                .firstName("Alice")
                .lastName("Smith")
                .build();

        activity = Activity.builder()
                .id(activityId)
                .title("Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Hall A")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(20)
                .build();

        enrollment = ActivityEnrollment.builder()
                .id(enrollmentId)
                .activity(activity)
                .resident(resident)
                .build();

        // Default: emailChannel supports EMAIL
        lenient().when(emailChannel.supports(Channel.EMAIL)).thenReturn(true);
        lenient().when(emailChannel.supports(Channel.SMS)).thenReturn(false);
        lenient().when(emailChannel.supports(Channel.PUSH)).thenReturn(false);

        // Default: save returns the log entry back
        lenient().when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // =======================================================================
    // sendActivityReminder
    // =======================================================================

    @Test
    void sendActivityReminder_happyPath_dispatchesForEachEnrollmentAndSavesLogSent() {
        // given
        when(activityEnrollmentRepository.findByActivityId(activityId))
                .thenReturn(List.of(enrollment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.ACTIVITY_REMINDER))
                .thenReturn(Optional.empty()); // fall-back to email-only
        when(emailChannel.send(any(NotificationMessage.class))).thenReturn(ChannelResult.success());

        // when
        notificationService.sendActivityReminder(activityId, UUID.randomUUID());

        // then: channel was called once
        verify(emailChannel, times(1)).send(any(NotificationMessage.class));

        // then: two saves — first RETRYING, then updated to SENT
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, atLeast(2)).save(logCaptor.capture());
        List<NotificationLog> savedLogs = logCaptor.getAllValues();
        NotificationLog finalLog = savedLogs.get(savedLogs.size() - 1);
        assertThat(finalLog.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(finalLog.getNotificationType()).isEqualTo(NotificationType.ACTIVITY_REMINDER);
    }

    @Test
    void sendActivityReminder_noEnrollments_nothingDispatched() {
        // given
        when(activityEnrollmentRepository.findByActivityId(activityId))
                .thenReturn(Collections.emptyList());

        // when
        notificationService.sendActivityReminder(activityId, UUID.randomUUID());

        // then
        verify(emailChannel, never()).send(any());
        verify(notificationLogRepository, never()).save(any());
    }

    // =======================================================================
    // sendCancellationNotice
    // =======================================================================

    @Test
    void sendCancellationNotice_happyPath_notifiesEnrolledResidentsWithCancelledType() {
        // given
        when(activityEnrollmentRepository.findByActivityId(activityId))
                .thenReturn(List.of(enrollment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.ACTIVITY_CANCELLED))
                .thenReturn(Optional.empty());
        when(emailChannel.send(any(NotificationMessage.class))).thenReturn(ChannelResult.success());

        // when
        notificationService.sendCancellationNotice(activityId);

        // then
        verify(emailChannel, times(1)).send(any(NotificationMessage.class));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, atLeast(2)).save(logCaptor.capture());
        NotificationLog finalLog = logCaptor.getAllValues().get(logCaptor.getAllValues().size() - 1);
        assertThat(finalLog.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(finalLog.getNotificationType()).isEqualTo(NotificationType.ACTIVITY_CANCELLED);
    }

    @Test
    void sendCancellationNotice_channelFailure_logSavedWithRetryingAndErrorMessage() {
        // given
        when(activityEnrollmentRepository.findByActivityId(activityId))
                .thenReturn(List.of(enrollment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.ACTIVITY_CANCELLED))
                .thenReturn(Optional.empty());
        when(emailChannel.send(any(NotificationMessage.class)))
                .thenReturn(ChannelResult.failure("SMTP timeout"));

        // when
        notificationService.sendCancellationNotice(activityId);

        // then
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, atLeast(2)).save(logCaptor.capture());
        // The final saved log should reflect the failure (attempt count 1 < MAX_RETRY_ATTEMPTS=3 → RETRYING)
        NotificationLog finalLog = logCaptor.getAllValues().get(logCaptor.getAllValues().size() - 1);
        assertThat(finalLog.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
        assertThat(finalLog.getErrorMessage()).isEqualTo("SMTP timeout");
        assertThat(finalLog.getAttemptCount()).isEqualTo(1);
    }

    // =======================================================================
    // sendEnrollmentConfirmation
    // =======================================================================

    @Test
    void sendEnrollmentConfirmation_happyPath_residentNotifiedWithEnrollmentConfirmed() {
        // given
        when(activityEnrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.ENROLLMENT_CONFIRMED))
                .thenReturn(Optional.empty());
        when(emailChannel.send(any(NotificationMessage.class))).thenReturn(ChannelResult.success());

        // when
        notificationService.sendEnrollmentConfirmation(enrollmentId);

        // then
        verify(emailChannel, times(1)).send(any(NotificationMessage.class));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, atLeast(2)).save(logCaptor.capture());
        NotificationLog finalLog = logCaptor.getAllValues().get(logCaptor.getAllValues().size() - 1);
        assertThat(finalLog.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(finalLog.getNotificationType()).isEqualTo(NotificationType.ENROLLMENT_CONFIRMED);
    }

    @Test
    void sendEnrollmentConfirmation_enrollmentNotFound_gracefulSkipNoDispatch() {
        // given — no enrollment found
        when(activityEnrollmentRepository.findById(enrollmentId)).thenReturn(Optional.empty());

        // when — should not throw
        notificationService.sendEnrollmentConfirmation(enrollmentId);

        // then
        verify(emailChannel, never()).send(any());
        verify(notificationLogRepository, never()).save(any());
    }

    // =======================================================================
    // sendBroadcast
    // =======================================================================

    @Test
    void sendBroadcast_happyPath_allTargetUsersNotifiedWithBroadcastType() {
        // given
        UUID userId2 = UUID.randomUUID();
        User user2 = User.builder()
                .id(userId2)
                .email("user2@example.com")
                .passwordHash("hash")
                .role(User.Role.STAFF)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId2)).thenReturn(Optional.of(user2));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(eq(userId), eq(NotificationType.BROADCAST)))
                .thenReturn(Optional.empty());
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(eq(userId2), eq(NotificationType.BROADCAST)))
                .thenReturn(Optional.empty());
        when(emailChannel.send(any(NotificationMessage.class))).thenReturn(ChannelResult.success());

        // when
        notificationService.sendBroadcast("Lunch menu updated", List.of(userId, userId2));

        // then — two dispatches, one per user
        verify(emailChannel, times(2)).send(any(NotificationMessage.class));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        // 4 saves total: initial RETRYING + updated SENT for each of the 2 users
        verify(notificationLogRepository, times(4)).save(logCaptor.capture());
        List<NotificationLog> allLogs = logCaptor.getAllValues();
        // Verify the last log for each pair is SENT
        assertThat(allLogs.get(1).getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(allLogs.get(3).getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void sendBroadcast_emptyTargetList_noDispatch() {
        // when
        notificationService.sendBroadcast("No one listening", Collections.emptyList());

        // then
        verify(emailChannel, never()).send(any());
        verify(notificationLogRepository, never()).save(any());
    }

    // =======================================================================
    // retryFailed
    // =======================================================================

    @Test
    void retryFailed_eligibleLogs_retriedAndStatusUpdatedToSentOnSuccess() {
        // given
        NotificationLog retryLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .user(user)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.RETRYING)
                .message("Reminder body")
                .attemptCount(1)
                .build();

        when(notificationLogRepository.findByStatusAndAttemptCountLessThan(DeliveryStatus.RETRYING, NotificationService.MAX_RETRY_ATTEMPTS))
                .thenReturn(List.of(retryLog));
        when(emailChannel.send(any(NotificationMessage.class))).thenReturn(ChannelResult.success());

        // when
        notificationService.retryFailed();

        // then
        assertThat(retryLog.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(retryLog.getErrorMessage()).isNull();
        verify(notificationLogRepository, times(1)).save(retryLog);
    }

    @Test
    void retryFailed_failedRetryBelowMax_attemptCountIncrementedStatusStaysRetrying() {
        // given — attemptCount is 1, MAX is 3, so after failure it should be 2 and still RETRYING
        NotificationLog retryLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .user(user)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.RETRYING)
                .message("Reminder body")
                .attemptCount(1)
                .build();

        when(notificationLogRepository.findByStatusAndAttemptCountLessThan(DeliveryStatus.RETRYING, NotificationService.MAX_RETRY_ATTEMPTS))
                .thenReturn(List.of(retryLog));
        when(emailChannel.send(any(NotificationMessage.class)))
                .thenReturn(ChannelResult.failure("Connection refused"));

        // when
        notificationService.retryFailed();

        // then
        assertThat(retryLog.getAttemptCount()).isEqualTo(2);
        assertThat(retryLog.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
        assertThat(retryLog.getErrorMessage()).isEqualTo("Connection refused");
        verify(notificationLogRepository, times(1)).save(retryLog);
    }

    @Test
    void retryFailed_failedRetryReachesMax_statusSetToFailed() {
        // given — attemptCount is 2, after failure becomes 3 == MAX → FAILED
        NotificationLog retryLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .user(user)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.RETRYING)
                .message("Reminder body")
                .attemptCount(2)
                .build();

        when(notificationLogRepository.findByStatusAndAttemptCountLessThan(DeliveryStatus.RETRYING, NotificationService.MAX_RETRY_ATTEMPTS))
                .thenReturn(List.of(retryLog));
        when(emailChannel.send(any(NotificationMessage.class)))
                .thenReturn(ChannelResult.failure("Permanent failure"));

        // when
        notificationService.retryFailed();

        // then
        assertThat(retryLog.getAttemptCount()).isEqualTo(NotificationService.MAX_RETRY_ATTEMPTS);
        assertThat(retryLog.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        verify(notificationLogRepository, times(1)).save(retryLog);
    }

    @Test
    void retryFailed_noEligibleLogs_noDispatch() {
        // given
        when(notificationLogRepository.findByStatusAndAttemptCountLessThan(DeliveryStatus.RETRYING, NotificationService.MAX_RETRY_ATTEMPTS))
                .thenReturn(Collections.emptyList());

        // when
        notificationService.retryFailed();

        // then
        verify(emailChannel, never()).send(any());
        verify(notificationLogRepository, never()).save(any());
    }

    // =======================================================================
    // Preference checks
    // =======================================================================

    @Test
    void preferenceCheck_emailOnlyPreference_onlyEmailChannelDispatched() {
        // given — preference: email=true, sms=false, push=false
        NotificationPreference emailOnlyPref = NotificationPreference.builder()
                .user(user)
                .notificationType(NotificationType.BROADCAST)
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.BROADCAST))
                .thenReturn(Optional.of(emailOnlyPref));
        when(emailChannel.send(any(NotificationMessage.class))).thenReturn(ChannelResult.success());

        // when
        notificationService.sendBroadcast("Test message", List.of(userId));

        // then — only EMAIL dispatched
        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
        // SMS and PUSH channels were never queried (no other channel mocks registered)
    }

    @Test
    void preferenceCheck_noPreferenceRecord_fallsBackToEmailOnly() {
        // given — no preference record found
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.BROADCAST))
                .thenReturn(Optional.empty());
        when(emailChannel.send(any(NotificationMessage.class))).thenReturn(ChannelResult.success());

        // when
        notificationService.sendBroadcast("Default fallback", List.of(userId));

        // then — email dispatched (fallback)
        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
    }

    @Test
    void preferenceCheck_allChannelsDisabled_noDispatch() {
        // given — preference: email=false, sms=false, push=false
        NotificationPreference allDisabledPref = NotificationPreference.builder()
                .user(user)
                .notificationType(NotificationType.BROADCAST)
                .emailEnabled(false)
                .smsEnabled(false)
                .pushEnabled(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.BROADCAST))
                .thenReturn(Optional.of(allDisabledPref));

        // when
        notificationService.sendBroadcast("Should not arrive", List.of(userId));

        // then — no channel dispatched at all
        verify(emailChannel, never()).send(any());
        verify(notificationLogRepository, never()).save(any());
    }
}
