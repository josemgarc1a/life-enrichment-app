package com.lifeenrichment.scheduler;

import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.repository.ActivityRepository;
import com.lifeenrichment.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationScheduler}.
 *
 * <p>Pure Mockito tests — no Spring context is loaded. The {@code reminderLeadMinutes}
 * field is injected via {@link ReflectionTestUtils} to mirror the {@code @Value} binding
 * used in production.
 */
@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private NotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "reminderLeadMinutes", 60);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Activity buildActivity(Activity.Status status) {
        return Activity.builder()
                .id(UUID.randomUUID())
                .title("Test Activity")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .capacity(20)
                .status(status)
                .build();
    }

    // -------------------------------------------------------------------------
    // sendReminders tests
    // -------------------------------------------------------------------------

    @Test
    void sendReminders_noActivitiesInWindow_doesNotCallService() {
        when(activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.sendReminders();

        verify(notificationService, never()).sendActivityReminder(any(UUID.class), any());
    }

    @Test
    void sendReminders_scheduledActivityInWindow_callsServiceOnce() {
        Activity activity = buildActivity(Activity.Status.SCHEDULED);
        when(activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(activity));

        scheduler.sendReminders();

        verify(notificationService, times(1)).sendActivityReminder(activity.getId(), null);
    }

    @Test
    void sendReminders_multipleActivitiesInWindow_callsServiceForEach() {
        Activity activity1 = buildActivity(Activity.Status.SCHEDULED);
        Activity activity2 = buildActivity(Activity.Status.SCHEDULED);
        Activity activity3 = buildActivity(Activity.Status.SCHEDULED);
        when(activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(activity1, activity2, activity3));

        scheduler.sendReminders();

        verify(notificationService, times(3)).sendActivityReminder(any(UUID.class), isNull());
    }

    @Test
    void sendReminders_cancelledActivityInWindow_doesNotCallService() {
        Activity cancelled = buildActivity(Activity.Status.CANCELLED);
        when(activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(cancelled));

        scheduler.sendReminders();

        verify(notificationService, never()).sendActivityReminder(any(UUID.class), any());
    }

    @Test
    void sendReminders_mixedStatusActivities_onlyDispatchesScheduled() {
        Activity scheduled1 = buildActivity(Activity.Status.SCHEDULED);
        Activity scheduled2 = buildActivity(Activity.Status.SCHEDULED);
        Activity cancelled  = buildActivity(Activity.Status.CANCELLED);
        when(activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(scheduled1, scheduled2, cancelled));

        scheduler.sendReminders();

        verify(notificationService, times(2)).sendActivityReminder(any(UUID.class), isNull());
    }

    @Test
    void sendReminders_repositoryThrowsException_doesNotPropagate() {
        when(activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertThatCode(() -> scheduler.sendReminders()).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // retryFailedNotifications tests
    // -------------------------------------------------------------------------

    @Test
    void retryFailedNotifications_callsRetryFailed() {
        scheduler.retryFailedNotifications();

        verify(notificationService, times(1)).retryFailed();
    }

    @Test
    void retryFailedNotifications_serviceThrowsException_doesNotPropagate() {
        doThrow(new RuntimeException("SMTP timeout")).when(notificationService).retryFailed();

        assertThatCode(() -> scheduler.retryFailedNotifications()).doesNotThrowAnyException();
    }
}
