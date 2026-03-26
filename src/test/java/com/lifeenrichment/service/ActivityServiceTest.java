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
}
