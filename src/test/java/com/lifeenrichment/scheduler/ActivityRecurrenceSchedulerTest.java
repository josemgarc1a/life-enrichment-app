package com.lifeenrichment.scheduler;

import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.repository.ActivityRepository;
import com.lifeenrichment.service.ActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityRecurrenceSchedulerTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityService activityService;

    @InjectMocks private ActivityRecurrenceScheduler scheduler;

    private Activity templateA;
    private Activity templateB;

    @BeforeEach
    void setUp() {
        templateA = Activity.builder()
                .title("Thursday Night Bingo")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(1))
                .capacity(20)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=TH")
                .status(Activity.Status.SCHEDULED)
                .build();

        templateB = Activity.builder()
                .title("Monday Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(LocalDateTime.now().plusDays(2))
                .endTime(LocalDateTime.now().plusDays(2).plusHours(1))
                .capacity(10)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=MO")
                .status(Activity.Status.SCHEDULED)
                .build();
    }

    @Test
    void fillRecurringWindow_callsExpandSeries_forEachTemplate() {
        when(activityRepository.findByRecurrenceRuleIsNotNullAndDeletedAtIsNullAndStatus(Activity.Status.SCHEDULED))
                .thenReturn(List.of(templateA, templateB));
        when(activityService.expandSeries(any(), eq(ActivityService.RECURRENCE_WINDOW_WEEKS)))
                .thenReturn(List.of());

        scheduler.fillRecurringWindow();

        verify(activityService, times(1)).expandSeries(templateA, ActivityService.RECURRENCE_WINDOW_WEEKS);
        verify(activityService, times(1)).expandSeries(templateB, ActivityService.RECURRENCE_WINDOW_WEEKS);
    }

    @Test
    void fillRecurringWindow_doesNothing_whenNoActiveTemplates() {
        when(activityRepository.findByRecurrenceRuleIsNotNullAndDeletedAtIsNullAndStatus(Activity.Status.SCHEDULED))
                .thenReturn(List.of());

        scheduler.fillRecurringWindow();

        verify(activityService, never()).expandSeries(any(), anyInt());
    }

    @Test
    void fillRecurringWindow_continuesProcessing_whenOneSeries_throwsException() {
        when(activityRepository.findByRecurrenceRuleIsNotNullAndDeletedAtIsNullAndStatus(Activity.Status.SCHEDULED))
                .thenReturn(List.of(templateA, templateB));
        when(activityService.expandSeries(eq(templateA), anyInt()))
                .thenThrow(new RuntimeException("DB error on series A"));
        when(activityService.expandSeries(eq(templateB), anyInt()))
                .thenReturn(List.of());

        // Should not throw — errors are caught and logged per series
        scheduler.fillRecurringWindow();

        verify(activityService, times(1)).expandSeries(templateB, ActivityService.RECURRENCE_WINDOW_WEEKS);
    }

    @Test
    void fillRecurringWindow_logsNewOccurrences_returnedByExpandSeries() {
        Activity occurrence = Activity.builder()
                .title("Thursday Night Bingo")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .startTime(LocalDateTime.now().plusDays(3))
                .endTime(LocalDateTime.now().plusDays(3).plusHours(1))
                .capacity(20)
                .seriesId(UUID.randomUUID())
                .build();

        when(activityRepository.findByRecurrenceRuleIsNotNullAndDeletedAtIsNullAndStatus(Activity.Status.SCHEDULED))
                .thenReturn(List.of(templateA));
        when(activityService.expandSeries(templateA, ActivityService.RECURRENCE_WINDOW_WEEKS))
                .thenReturn(List.of(occurrence));

        scheduler.fillRecurringWindow();

        verify(activityService).expandSeries(templateA, ActivityService.RECURRENCE_WINDOW_WEEKS);
    }
}
