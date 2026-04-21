package com.lifeenrichment.service;

import com.lifeenrichment.dto.request.LogAttendanceRequest;
import com.lifeenrichment.dto.response.ActivityAttendanceSummaryResponse;
import com.lifeenrichment.dto.response.AttendanceLogResponse;
import com.lifeenrichment.dto.response.ResidentParticipationResponse;
import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.entity.AttendanceLog;
import com.lifeenrichment.entity.AttendanceLog.AssistanceLevel;
import com.lifeenrichment.entity.AttendanceLog.AttendanceStatus;
import com.lifeenrichment.entity.Resident;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.repository.ActivityEnrollmentRepository;
import com.lifeenrichment.repository.ActivityRepository;
import com.lifeenrichment.repository.AttendanceLogRepository;
import com.lifeenrichment.repository.ResidentRepository;
import com.lifeenrichment.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityEnrollmentRepository enrollmentRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AttendanceService attendanceService;

    private UUID activityId;
    private UUID residentId;
    private UUID staffId;

    private Activity activity;
    private Resident alice;
    private Resident bob;
    private User staff;
    private AttendanceLog aliceLog;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 21, 10, 0);

    @BeforeEach
    void setUp() {
        activityId = UUID.randomUUID();
        residentId = UUID.randomUUID();
        staffId    = UUID.randomUUID();

        staff = User.builder()
                .id(staffId)
                .email("staff@facility.com")
                .role(User.Role.STAFF)
                .build();

        alice = Resident.builder()
                .id(residentId)
                .firstName("Alice").lastName("Johnson")
                .careLevel(Resident.CareLevel.LOW)
                .isActive(true)
                .build();

        bob = Resident.builder()
                .id(UUID.randomUUID())
                .firstName("Bob").lastName("Smith")
                .careLevel(Resident.CareLevel.MEDIUM)
                .isActive(true)
                .build();

        activity = Activity.builder()
                .id(activityId)
                .title("Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(NOW)
                .endTime(NOW.plusHours(1))
                .capacity(10)
                .status(Activity.Status.COMPLETED)
                .build();

        aliceLog = AttendanceLog.builder()
                .id(UUID.randomUUID())
                .activity(activity)
                .resident(alice)
                .status(AttendanceStatus.ATTENDED)
                .assistanceLevel(AssistanceLevel.NONE)
                .loggedBy(staff)
                .loggedAt(NOW.plusHours(1))
                .build();
    }

    // ── logAttendance — happy path ────────────────────────────────────────────

    @Test
    void logAttendance_createsNewLog_whenNoneExists() {
        LogAttendanceRequest request = LogAttendanceRequest.builder()
                .activityId(activityId)
                .residentId(residentId)
                .status(AttendanceStatus.ATTENDED)
                .assistanceLevel(AssistanceLevel.MINIMAL)
                .assistanceNotes("Needed chair support")
                .build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(activity));
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(alice));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId)).thenReturn(true);
        when(attendanceLogRepository.findByActivityIdAndResidentId(activityId, residentId)).thenReturn(Optional.empty());
        when(attendanceLogRepository.save(any())).thenReturn(aliceLog);

        AttendanceLogResponse response = attendanceService.logAttendance(request, staffId);

        assertThat(response.getActivityId()).isEqualTo(activityId);
        assertThat(response.getResidentId()).isEqualTo(residentId);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        verify(attendanceLogRepository).save(any());
    }

    @Test
    void logAttendance_updatesExistingLog_whenDuplicateExists() {
        LogAttendanceRequest request = LogAttendanceRequest.builder()
                .activityId(activityId)
                .residentId(residentId)
                .status(AttendanceStatus.ABSENT)
                .assistanceLevel(AssistanceLevel.NONE)
                .build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(activity));
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(alice));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId)).thenReturn(true);
        when(attendanceLogRepository.findByActivityIdAndResidentId(activityId, residentId))
                .thenReturn(Optional.of(aliceLog));
        when(attendanceLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceLogResponse response = attendanceService.logAttendance(request, staffId);

        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        verify(attendanceLogRepository, times(1)).save(aliceLog);
    }

    // ── logAttendance — validation failures ───────────────────────────────────

    @Test
    void logAttendance_throwsResourceNotFound_whenActivityNotFound() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                attendanceService.logAttendance(LogAttendanceRequest.builder()
                        .activityId(activityId).residentId(residentId)
                        .status(AttendanceStatus.ATTENDED).build(), staffId));
    }

    @Test
    void logAttendance_throwsResourceNotFound_whenResidentNotFound() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(activity));
        when(residentRepository.findById(residentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                attendanceService.logAttendance(LogAttendanceRequest.builder()
                        .activityId(activityId).residentId(residentId)
                        .status(AttendanceStatus.ATTENDED).build(), staffId));
    }

    @Test
    void logAttendance_throwsResourceNotFound_whenUserNotFound() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(activity));
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(alice));
        when(userRepository.findById(staffId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                attendanceService.logAttendance(LogAttendanceRequest.builder()
                        .activityId(activityId).residentId(residentId)
                        .status(AttendanceStatus.ATTENDED).build(), staffId));
    }

    @Test
    void logAttendance_throwsBusinessException_whenResidentNotEnrolled() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(activity));
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(alice));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId)).thenReturn(false);

        assertThrows(BusinessException.class, () ->
                attendanceService.logAttendance(LogAttendanceRequest.builder()
                        .activityId(activityId).residentId(residentId)
                        .status(AttendanceStatus.ATTENDED).build(), staffId));
    }

    // ── getResidentHistory ────────────────────────────────────────────────────

    @Test
    void getResidentHistory_returnsFullHistory_whenNoDatesProvided() {
        when(residentRepository.existsById(residentId)).thenReturn(true);
        when(attendanceLogRepository.findByResidentIdOrderByLoggedAtDesc(residentId))
                .thenReturn(List.of(aliceLog));

        List<AttendanceLogResponse> history = attendanceService.getResidentHistory(residentId, null, null);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getActivityTitle()).isEqualTo("Morning Yoga");
        verify(attendanceLogRepository).findByResidentIdOrderByLoggedAtDesc(residentId);
        verify(attendanceLogRepository, never()).findByResidentIdAndLoggedAtBetween(any(), any(), any());
    }

    @Test
    void getResidentHistory_usesDateFilter_whenBothDatesProvided() {
        LocalDateTime from = NOW.minusDays(7);
        LocalDateTime to   = NOW.plusDays(1);

        when(residentRepository.existsById(residentId)).thenReturn(true);
        when(attendanceLogRepository.findByResidentIdAndLoggedAtBetween(residentId, from, to))
                .thenReturn(List.of(aliceLog));

        List<AttendanceLogResponse> history = attendanceService.getResidentHistory(residentId, from, to);

        assertThat(history).hasSize(1);
        verify(attendanceLogRepository).findByResidentIdAndLoggedAtBetween(residentId, from, to);
        verify(attendanceLogRepository, never()).findByResidentIdOrderByLoggedAtDesc(any());
    }

    @Test
    void getResidentHistory_throwsResourceNotFound_whenResidentDoesNotExist() {
        when(residentRepository.existsById(residentId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                attendanceService.getResidentHistory(residentId, null, null));
    }

    // ── getActivitySummary ────────────────────────────────────────────────────

    @Test
    void getActivitySummary_returnsCorrectCounts() {
        AttendanceLog bobLog = AttendanceLog.builder()
                .id(UUID.randomUUID()).activity(activity).resident(bob)
                .status(AttendanceStatus.ABSENT).assistanceLevel(AssistanceLevel.NONE)
                .loggedBy(staff).build();

        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(activity));
        when(attendanceLogRepository.findByActivityId(activityId)).thenReturn(List.of(aliceLog, bobLog));

        ActivityAttendanceSummaryResponse summary = attendanceService.getActivitySummary(activityId);

        assertThat(summary.getActivityTitle()).isEqualTo("Morning Yoga");
        assertThat(summary.getTotalLogged()).isEqualTo(2);
        assertThat(summary.getAttended()).isEqualTo(1);
        assertThat(summary.getAbsent()).isEqualTo(1);
        assertThat(summary.getDeclined()).isEqualTo(0);
        assertThat(summary.getAttendanceRate()).isEqualTo(50.0);
    }

    @Test
    void getActivitySummary_returnsZeroRate_whenNoLogsExist() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.of(activity));
        when(attendanceLogRepository.findByActivityId(activityId)).thenReturn(List.of());

        ActivityAttendanceSummaryResponse summary = attendanceService.getActivitySummary(activityId);

        assertThat(summary.getTotalLogged()).isZero();
        assertThat(summary.getAttendanceRate()).isZero();
    }

    @Test
    void getActivitySummary_throwsResourceNotFound_whenActivityDoesNotExist() {
        when(activityRepository.findByIdAndDeletedAtIsNull(activityId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                attendanceService.getActivitySummary(activityId));
    }

    // ── getLowParticipationResidents ──────────────────────────────────────────

    @Test
    void getLowParticipationResidents_flagsResidentsBelowThreshold() {
        // Alice attended 1 of 3 activities = 33% — below 50% threshold
        AttendanceLog log2 = AttendanceLog.builder().activity(activity).resident(alice)
                .status(AttendanceStatus.ABSENT).assistanceLevel(AssistanceLevel.NONE)
                .loggedBy(staff).build();
        AttendanceLog log3 = AttendanceLog.builder().activity(activity).resident(alice)
                .status(AttendanceStatus.ABSENT).assistanceLevel(AssistanceLevel.NONE)
                .loggedBy(staff).build();

        when(residentRepository.findAll()).thenReturn(List.of(alice));
        when(attendanceLogRepository.findByResidentIdAndLoggedAtBetween(eq(alice.getId()), any(), any()))
                .thenReturn(List.of(aliceLog, log2, log3));

        List<ResidentParticipationResponse> flagged =
                attendanceService.getLowParticipationResidents(50, 30);

        assertThat(flagged).hasSize(1);
        assertThat(flagged.get(0).getResidentName()).isEqualTo("Alice Johnson");
        assertThat(flagged.get(0).getParticipationRate()).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.1));
        assertThat(flagged.get(0).isFlaggedAsLow()).isTrue();
    }

    @Test
    void getLowParticipationResidents_excludesResidentsAboveThreshold() {
        // Alice attended 1 of 1 = 100% — above 50% threshold
        when(residentRepository.findAll()).thenReturn(List.of(alice));
        when(attendanceLogRepository.findByResidentIdAndLoggedAtBetween(eq(alice.getId()), any(), any()))
                .thenReturn(List.of(aliceLog));

        List<ResidentParticipationResponse> flagged =
                attendanceService.getLowParticipationResidents(50, 30);

        assertThat(flagged).isEmpty();
    }

    @Test
    void getLowParticipationResidents_usesDefaults_whenZerosPassed() {
        when(residentRepository.findAll()).thenReturn(List.of(alice));
        when(attendanceLogRepository.findByResidentIdAndLoggedAtBetween(any(), any(), any()))
                .thenReturn(List.of());

        // Resident with no logs has 0% rate — should be flagged below default 50%
        List<ResidentParticipationResponse> flagged =
                attendanceService.getLowParticipationResidents(0, 0);

        assertThat(flagged).hasSize(1);
        assertThat(flagged.get(0).getParticipationRate()).isZero();
    }

    @Test
    void getLowParticipationResidents_skipsInactiveResidents() {
        Resident inactive = Resident.builder()
                .id(UUID.randomUUID()).firstName("Inactive").lastName("User")
                .careLevel(Resident.CareLevel.LOW).isActive(false).build();

        when(residentRepository.findAll()).thenReturn(List.of(inactive));

        List<ResidentParticipationResponse> flagged =
                attendanceService.getLowParticipationResidents(50, 30);

        assertThat(flagged).isEmpty();
        verify(attendanceLogRepository, never()).findByResidentIdAndLoggedAtBetween(any(), any(), any());
    }
}
