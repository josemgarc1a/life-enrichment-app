package com.lifeenrichment.service;

import com.lifeenrichment.dto.request.CreateActivityRequest;
import com.lifeenrichment.dto.request.EnrollResidentRequest;
import com.lifeenrichment.dto.request.UpdateActivityRequest;
import com.lifeenrichment.dto.response.ActivityResponse;
import com.lifeenrichment.dto.response.CalendarEventResponse;
import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.entity.ActivityEnrollment;
import com.lifeenrichment.entity.Resident;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.repository.ActivityEnrollmentRepository;
import com.lifeenrichment.repository.ActivityRepository;
import com.lifeenrichment.repository.ResidentRepository;
import com.lifeenrichment.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityEnrollmentRepository enrollmentRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ActivityService activityService;

    private UUID activityId;
    private UUID userId;
    private UUID residentId;
    private User director;
    private Resident resident;
    private Activity scheduledActivity;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 10, 0);

    @BeforeEach
    void setUp() {
        activityId  = UUID.randomUUID();
        userId      = UUID.randomUUID();
        residentId  = UUID.randomUUID();

        director = User.builder()
                .id(userId)
                .email("director@facility.com")
                .role(User.Role.DIRECTOR)
                .build();

        resident = Resident.builder()
                .id(residentId)
                .firstName("Alice")
                .lastName("Johnson")
                .careLevel(Resident.CareLevel.LOW)
                .isActive(true)
                .build();

        scheduledActivity = Activity.builder()
                .id(activityId)
                .title("Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(10)
                .status(Activity.Status.SCHEDULED)
                .createdBy(director)
                .build();
    }

    // ── createActivity ────────────────────────────────────────────────────────

    @Test
    void createActivity_persistsAndReturnsResponse() {
        CreateActivityRequest request = CreateActivityRequest.builder()
                .title("Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(10)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(director));
        when(activityRepository.save(any())).thenReturn(scheduledActivity);
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        ActivityResponse response = activityService.createActivity(request, userId);

        assertThat(response.getTitle()).isEqualTo("Morning Yoga");
        assertThat(response.getStatus()).isEqualTo(Activity.Status.SCHEDULED);
        assertThat(response.getEnrollmentCount()).isZero();
        verify(activityRepository).save(any(Activity.class));
    }

    @Test
    void createActivity_throwsNotFound_whenCreatorMissing() {
        CreateActivityRequest request = CreateActivityRequest.builder()
                .title("Yoga")
                .category(Activity.Category.FITNESS)
                .location("Room")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(5)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> activityService.createActivity(request, userId));
    }

    // ── getActivity ───────────────────────────────────────────────────────────

    @Test
    void getActivity_returnsResponse_whenActive() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        ActivityResponse response = activityService.getActivity(activityId);

        assertThat(response.getId()).isEqualTo(activityId);
        assertThat(response.getTitle()).isEqualTo("Morning Yoga");
    }

    @Test
    void getActivity_throwsNotFound_whenDeleted() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> activityService.getActivity(activityId));
    }

    // ── updateActivity ────────────────────────────────────────────────────────

    @Test
    void updateActivity_appliesOnlyNonNullFields() {
        UpdateActivityRequest request = UpdateActivityRequest.builder()
                .title("Evening Yoga")
                .capacity(15)
                .build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));
        when(activityRepository.save(any())).thenReturn(scheduledActivity);
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        activityService.updateActivity(activityId, request);

        assertThat(scheduledActivity.getTitle()).isEqualTo("Evening Yoga");
        assertThat(scheduledActivity.getCapacity()).isEqualTo(15);
        // Location not provided — should remain unchanged
        assertThat(scheduledActivity.getLocation()).isEqualTo("Garden Room");
        verify(activityRepository).save(scheduledActivity);
    }

    // ── cancelActivity ────────────────────────────────────────────────────────

    @Test
    void cancelActivity_setsStatusToCancelled() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));
        when(activityRepository.save(any())).thenReturn(scheduledActivity);
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        activityService.cancelActivity(activityId);

        assertThat(scheduledActivity.getStatus()).isEqualTo(Activity.Status.CANCELLED);
        verify(activityRepository).save(scheduledActivity);
    }

    @Test
    void cancelActivity_throwsNotFound_whenMissing() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> activityService.cancelActivity(activityId));
    }

    // ── deleteActivity ────────────────────────────────────────────────────────

    @Test
    void deleteActivity_setsDeletedAt() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));
        when(activityRepository.save(any())).thenReturn(scheduledActivity);

        activityService.deleteActivity(activityId);

        assertThat(scheduledActivity.getDeletedAt()).isNotNull();
        verify(activityRepository).save(scheduledActivity);
    }

    // ── enrollResident ────────────────────────────────────────────────────────

    @Test
    void enrollResident_savesEnrollment_whenValid() {
        EnrollResidentRequest request = EnrollResidentRequest.builder()
                .residentId(residentId).build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));
        when(enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId))
                .thenReturn(false);
        when(enrollmentRepository.countByActivityId(activityId)).thenReturn(5L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(director));
        when(enrollmentRepository.save(any())).thenReturn(ActivityEnrollment.builder()
                .activity(scheduledActivity).resident(resident).build());
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(scheduledActivity));
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        activityService.enrollResident(activityId, request, userId);

        verify(enrollmentRepository).save(any(ActivityEnrollment.class));
    }

    @Test
    void enrollResident_throwsBusiness_whenAlreadyEnrolled() {
        EnrollResidentRequest request = EnrollResidentRequest.builder()
                .residentId(residentId).build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));
        when(enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId))
                .thenReturn(true);

        assertThrows(BusinessException.class,
                () -> activityService.enrollResident(activityId, request, userId));
    }

    @Test
    void enrollResident_throwsBusiness_whenCapacityFull() {
        EnrollResidentRequest request = EnrollResidentRequest.builder()
                .residentId(residentId).build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));  // capacity = 10
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));
        when(enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId))
                .thenReturn(false);
        when(enrollmentRepository.countByActivityId(activityId)).thenReturn(10L); // at capacity

        assertThrows(BusinessException.class,
                () -> activityService.enrollResident(activityId, request, userId));
    }

    // ── unenrollResident ──────────────────────────────────────────────────────

    @Test
    void unenrollResident_deletesEnrollment_whenExists() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));
        when(enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId))
                .thenReturn(true);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(scheduledActivity));
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        activityService.unenrollResident(activityId, residentId);

        verify(enrollmentRepository).deleteByActivityIdAndResidentId(activityId, residentId);
    }

    @Test
    void unenrollResident_throwsNotFound_whenEnrollmentMissing() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));
        when(enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId))
                .thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> activityService.unenrollResident(activityId, residentId));
    }

    // ── getCalendarEvents ─────────────────────────────────────────────────────

    @Test
    void getCalendarEvents_returnsEventsInRange() {
        when(activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of(scheduledActivity));
        when(enrollmentRepository.countByActivityId(activityId)).thenReturn(3L);

        List<CalendarEventResponse> events = activityService.getCalendarEvents(NOW, NOW.plusDays(7));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTitle()).isEqualTo("Morning Yoga");
        assertThat(events.get(0).getEnrollmentCount()).isEqualTo(3L);
    }

    @Test
    void getCalendarEvents_returnsEmpty_whenNoActivitiesInRange() {
        when(activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());

        List<CalendarEventResponse> events = activityService.getCalendarEvents(NOW, NOW.plusDays(7));

        assertThat(events).isEmpty();
    }

    // ── listActivities ────────────────────────────────────────────────────────

    @Test
    void listActivities_withNoFilters_callsCorrectRepository() {
        Page<Activity> page = new PageImpl<>(List.of(scheduledActivity));
        when(activityRepository.findByDeletedAtIsNull(any())).thenReturn(page);
        when(enrollmentRepository.findByActivityId(any())).thenReturn(List.of());

        Page<ActivityResponse> result = activityService.listActivities(null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(activityRepository).findByDeletedAtIsNull(any());
    }

    @Test
    void listActivities_withCategoryAndStatus_callsCorrectRepository() {
        Page<Activity> page = new PageImpl<>(List.of(scheduledActivity));
        when(activityRepository.findByDeletedAtIsNullAndCategoryAndStatus(any(), any(), any()))
                .thenReturn(page);
        when(enrollmentRepository.findByActivityId(any())).thenReturn(List.of());

        Page<ActivityResponse> result = activityService.listActivities(
                Activity.Category.FITNESS, Activity.Status.SCHEDULED, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(activityRepository).findByDeletedAtIsNullAndCategoryAndStatus(
                Activity.Category.FITNESS, Activity.Status.SCHEDULED, PageRequest.of(0, 10));
    }

    // ── buildRrule ────────────────────────────────────────────────────────────

    @Test
    void buildRrule_returnsCorrectRrule_forThursday() {
        assertThat(activityService.buildRrule("THURSDAY")).isEqualTo("FREQ=WEEKLY;BYDAY=TH");
    }

    @Test
    void buildRrule_isCaseInsensitive() {
        assertThat(activityService.buildRrule("thursday")).isEqualTo("FREQ=WEEKLY;BYDAY=TH");
        assertThat(activityService.buildRrule("Monday")).isEqualTo("FREQ=WEEKLY;BYDAY=MO");
    }

    @Test
    void buildRrule_throwsBusinessException_forInvalidDay() {
        assertThrows(com.lifeenrichment.exception.BusinessException.class,
                () -> activityService.buildRrule("FUNDAY"));
    }

    // ── parseDayOfWeek ────────────────────────────────────────────────────────

    @Test
    void parseDayOfWeek_returnsCorrectDay_forValidRrule() {
        assertThat(activityService.parseDayOfWeek("FREQ=WEEKLY;BYDAY=TH"))
                .isEqualTo(java.time.DayOfWeek.THURSDAY);
        assertThat(activityService.parseDayOfWeek("FREQ=WEEKLY;BYDAY=MO"))
                .isEqualTo(java.time.DayOfWeek.MONDAY);
    }

    @Test
    void parseDayOfWeek_throwsBusinessException_whenBydayMissing() {
        assertThrows(com.lifeenrichment.exception.BusinessException.class,
                () -> activityService.parseDayOfWeek("FREQ=WEEKLY"));
    }

    @Test
    void parseDayOfWeek_throwsBusinessException_forUnknownByday() {
        assertThrows(com.lifeenrichment.exception.BusinessException.class,
                () -> activityService.parseDayOfWeek("FREQ=WEEKLY;BYDAY=XX"));
    }

    // ── expandSeries ─────────────────────────────────────────────────────────

    @Test
    void expandSeries_generates8Occurrences_forWeeklyRecurrence() {
        Activity template = Activity.builder()
                .id(UUID.randomUUID())
                .title("Thursday Night Bingo")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .startTime(LocalDateTime.now().plusDays(1).withHour(18).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(1).withHour(19).withMinute(0))
                .capacity(20)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=TH")
                .createdBy(director)
                .build();

        when(activityRepository.existsBySeriesIdAndStartTime(eq(template.getId()), any()))
                .thenReturn(false);
        when(activityRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Activity> occurrences = activityService.expandSeries(template, 8);

        assertThat(occurrences).hasSize(8);
        occurrences.forEach(o -> {
            assertThat(o.getSeriesId()).isEqualTo(template.getId());
            assertThat(o.getRecurrenceRule()).isNull();
            assertThat(o.getTitle()).isEqualTo(template.getTitle());
            assertThat(o.getStartTime().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.THURSDAY);
        });
    }

    @Test
    void expandSeries_skipsExistingOccurrences_isIdempotent() {
        Activity template = Activity.builder()
                .id(UUID.randomUUID())
                .title("Monday Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden")
                .startTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0))
                .capacity(10)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=MO")
                .createdBy(director)
                .build();

        // All dates already exist
        when(activityRepository.existsBySeriesIdAndStartTime(eq(template.getId()), any()))
                .thenReturn(true);
        when(activityRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Activity> occurrences = activityService.expandSeries(template, 8);

        assertThat(occurrences).isEmpty();
    }

    @Test
    void expandSeries_occurrencesHaveCorrectDuration() {
        Activity template = Activity.builder()
                .id(UUID.randomUUID())
                .title("Friday Arts")
                .category(Activity.Category.ARTS)
                .location("Arts Room")
                .startTime(LocalDateTime.now().plusDays(1).withHour(14).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(1).withHour(16).withMinute(0)) // 2-hour session
                .capacity(8)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=FR")
                .createdBy(director)
                .build();

        when(activityRepository.existsBySeriesIdAndStartTime(any(), any())).thenReturn(false);
        when(activityRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Activity> occurrences = activityService.expandSeries(template, 8);

        occurrences.forEach(o -> {
            long durationHours = java.time.Duration.between(o.getStartTime(), o.getEndTime()).toHours();
            assertThat(durationHours).isEqualTo(2);
            assertThat(o.getStartTime().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.FRIDAY);
        });
    }

    // ── createActivity (recurring) ────────────────────────────────────────────

    @Test
    void createActivity_recurring_generatesOccurrencesAndReturnsCount() {
        CreateActivityRequest request = CreateActivityRequest.builder()
                .title("Thursday Night Bingo")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .startTime(NOW.plusDays(3))
                .endTime(NOW.plusDays(3).plusHours(1))
                .capacity(20)
                .recurring(true)
                .dayOfWeek("THURSDAY")
                .build();

        Activity savedTemplate = Activity.builder()
                .id(activityId)
                .title("Thursday Night Bingo")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .startTime(NOW.plusDays(3))
                .endTime(NOW.plusDays(3).plusHours(1))
                .capacity(20)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=TH")
                .status(Activity.Status.SCHEDULED)
                .createdBy(director)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(director));
        when(activityRepository.save(any())).thenReturn(savedTemplate);
        when(activityRepository.existsBySeriesIdAndStartTime(any(), any())).thenReturn(false);
        when(activityRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        ActivityResponse response = activityService.createActivity(request, userId);

        assertThat(response.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY;BYDAY=TH");
        assertThat(response.getOccurrenceCount()).isEqualTo(8);
    }

    @Test
    void createActivity_nonRecurring_doesNotCallExpandSeries() {
        CreateActivityRequest request = CreateActivityRequest.builder()
                .title("One-off Event")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(10)
                .recurring(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(director));
        when(activityRepository.save(any())).thenReturn(scheduledActivity);
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        ActivityResponse response = activityService.createActivity(request, userId);

        assertThat(response.getOccurrenceCount()).isNull();
        verify(activityRepository, never()).existsBySeriesIdAndStartTime(any(), any());
    }

    // ── cancelSeries ─────────────────────────────────────────────────────────

    @Test
    void cancelSeries_softDeletesFutureOccurrencesAndCancelsTemplate() {
        Activity template = Activity.builder()
                .id(activityId)
                .title("Thursday Night Bingo")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(20)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=TH")
                .status(Activity.Status.SCHEDULED)
                .createdBy(director)
                .build();

        Activity futureOccurrence = Activity.builder()
                .id(UUID.randomUUID())
                .title("Thursday Night Bingo")
                .seriesId(activityId)
                .startTime(LocalDateTime.now().plusDays(7))
                .endTime(LocalDateTime.now().plusDays(7).plusHours(1))
                .capacity(20)
                .build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(template));
        when(activityRepository.findBySeriesIdAndStartTimeAfterAndDeletedAtIsNull(eq(activityId), any()))
                .thenReturn(List.of(futureOccurrence));
        when(activityRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(activityRepository.save(any())).thenReturn(template);
        when(enrollmentRepository.findByActivityId(activityId)).thenReturn(List.of());

        activityService.cancelSeries(activityId);

        assertThat(futureOccurrence.getDeletedAt()).isNotNull();
        assertThat(template.getStatus()).isEqualTo(Activity.Status.CANCELLED);
    }

    @Test
    void cancelSeries_throwsBusinessException_forNonRecurringActivity() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity)); // no recurrenceRule

        assertThrows(com.lifeenrichment.exception.BusinessException.class,
                () -> activityService.cancelSeries(activityId));
    }

    // ── updateSeries ─────────────────────────────────────────────────────────

    @Test
    void updateSeries_propagatesChangesToFutureOccurrences() {
        Activity template = Activity.builder()
                .id(activityId)
                .title("Thursday Night Bingo")
                .category(Activity.Category.SOCIAL)
                .location("Main Hall")
                .capacity(20)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=TH")
                .status(Activity.Status.SCHEDULED)
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .createdBy(director)
                .build();

        Activity occurrence = Activity.builder()
                .id(UUID.randomUUID())
                .title("Thursday Night Bingo")
                .location("Main Hall")
                .capacity(20)
                .seriesId(activityId)
                .startTime(LocalDateTime.now().plusDays(7))
                .endTime(LocalDateTime.now().plusDays(7).plusHours(1))
                .build();

        UpdateActivityRequest request = UpdateActivityRequest.builder()
                .location("Community Room")
                .capacity(25)
                .build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(template));
        when(activityRepository.findBySeriesIdAndStartTimeAfterAndDeletedAtIsNull(eq(activityId), any()))
                .thenReturn(List.of(occurrence));
        when(activityRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(activityRepository.save(any())).thenReturn(template);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(template));
        when(enrollmentRepository.findByActivityId(any())).thenReturn(List.of());

        activityService.updateSeries(activityId, request);

        assertThat(occurrence.getLocation()).isEqualTo("Community Room");
        assertThat(occurrence.getCapacity()).isEqualTo(25);
    }

    @Test
    void updateSeries_throwsBusinessException_forNonRecurringActivity() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId))
                .thenReturn(Optional.of(scheduledActivity));

        assertThrows(com.lifeenrichment.exception.BusinessException.class,
                () -> activityService.updateSeries(activityId, UpdateActivityRequest.builder().build()));
    }
}
