package com.lifeenrichment.service;

import com.lifeenrichment.dto.request.LogAttendanceRequest;
import com.lifeenrichment.dto.response.ActivityAttendanceSummaryResponse;
import com.lifeenrichment.dto.response.AttendanceLogResponse;
import com.lifeenrichment.dto.response.ResidentParticipationResponse;
import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.entity.ActivityEnrollment;
import com.lifeenrichment.entity.AttendanceLog;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for attendance logging and participation reporting.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li><strong>Upsert on duplicate</strong> — if a log already exists for the same
 *       activity + resident pair, {@code logAttendance} updates it rather than throwing.</li>
 *   <li><strong>Enrollment gate</strong> — only residents enrolled in an activity
 *       (via {@code ActivityEnrollment}) may have attendance logged.</li>
 *   <li><strong>Participation rate</strong> — calculated over a rolling lookback window
 *       (default 30 days) as {@code attended / totalLogged * 100}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    static final int DEFAULT_THRESHOLD_PERCENT = 50;
    static final int DEFAULT_LOOKBACK_DAYS      = 30;

    private final AttendanceLogRepository attendanceLogRepository;
    private final ActivityRepository activityRepository;
    private final ActivityEnrollmentRepository enrollmentRepository;
    private final ResidentRepository residentRepository;
    private final UserRepository userRepository;

    // ── Log attendance ────────────────────────────────────────────────────────

    /**
     * Creates or updates an attendance log for a resident at an activity.
     *
     * <p>If a log already exists for the {@code (activityId, residentId)} pair it is
     * updated in place (upsert). The resident must be enrolled in the activity.
     *
     * @param request       attendance details
     * @param loggedByUserId ID of the authenticated staff member
     * @return the created or updated log
     * @throws ResourceNotFoundException if the activity, resident, or user does not exist
     * @throws BusinessException         if the resident is not enrolled and {@code walkOn} is false
     */
    @Transactional
    public AttendanceLogResponse logAttendance(LogAttendanceRequest request, UUID loggedByUserId) {
        Activity activity = activityRepository.findByIdAndDeletedAtIsNull(request.getActivityId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity", request.getActivityId()));

        Resident resident = residentRepository.findById(request.getResidentId())
                .orElseThrow(() -> new ResourceNotFoundException("Resident", request.getResidentId()));

        User loggedBy = userRepository.findById(loggedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", loggedByUserId));

        boolean enrolled = enrollmentRepository.existsByActivityIdAndResidentId(
                request.getActivityId(), request.getResidentId());

        if (!enrolled && !request.isWalkOn()) {
            throw new BusinessException("Resident " + request.getResidentId()
                    + " is not enrolled in activity " + request.getActivityId()
                    + ". Set walkOn=true to log attendance for an unenrolled resident.");
        }

        if (!enrolled && request.isWalkOn()) {
            enrollmentRepository.save(ActivityEnrollment.builder()
                    .activity(activity)
                    .resident(resident)
                    .enrolledBy(loggedBy)
                    .build());
            log.info("Walk-on enrollment created: activity={} resident={} by={}",
                    request.getActivityId(), request.getResidentId(), loggedByUserId);
        }

        AttendanceLog entry = attendanceLogRepository
                .findByActivityIdAndResidentId(request.getActivityId(), request.getResidentId())
                .orElseGet(() -> AttendanceLog.builder()
                        .activity(activity)
                        .resident(resident)
                        .loggedBy(loggedBy)
                        .build());

        entry.setStatus(request.getStatus());
        entry.setAssistanceLevel(request.getAssistanceLevel());
        entry.setAssistanceNotes(request.getAssistanceNotes());
        entry.setLoggedBy(loggedBy);

        AttendanceLog saved = attendanceLogRepository.save(entry);
        log.info("Attendance logged: activity={} resident={} status={} by={}",
                request.getActivityId(), request.getResidentId(), request.getStatus(), loggedByUserId);

        return toResponse(saved);
    }

    // ── Resident history ──────────────────────────────────────────────────────

    /**
     * Returns attendance history for a resident, ordered most-recent-first.
     * When both {@code from} and {@code to} are provided the results are date-filtered;
     * if either is null the full history is returned.
     *
     * @throws ResourceNotFoundException if the resident does not exist
     */
    @Transactional(readOnly = true)
    public List<AttendanceLogResponse> getResidentHistory(UUID residentId,
                                                          LocalDateTime from,
                                                          LocalDateTime to) {
        if (!residentRepository.existsById(residentId)) {
            throw new ResourceNotFoundException("Resident", residentId);
        }

        List<AttendanceLog> logs = (from != null && to != null)
                ? attendanceLogRepository.findByResidentIdAndLoggedAtBetween(residentId, from, to)
                : attendanceLogRepository.findByResidentIdOrderByLoggedAtDesc(residentId);

        return logs.stream().map(this::toResponse).toList();
    }

    // ── Activity summary ──────────────────────────────────────────────────────

    /**
     * Returns an attendance summary for an activity — counts by status and overall rate.
     *
     * @throws ResourceNotFoundException if the activity does not exist
     */
    @Transactional(readOnly = true)
    public ActivityAttendanceSummaryResponse getActivitySummary(UUID activityId) {
        Activity activity = activityRepository.findByIdAndDeletedAtIsNull(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));

        List<AttendanceLog> logs = attendanceLogRepository.findByActivityId(activityId);

        long attended = logs.stream().filter(l -> l.getStatus() == AttendanceStatus.ATTENDED).count();
        long absent   = logs.stream().filter(l -> l.getStatus() == AttendanceStatus.ABSENT).count();
        long declined = logs.stream().filter(l -> l.getStatus() == AttendanceStatus.DECLINED).count();
        long total    = logs.size();

        double rate = total > 0 ? (double) attended / total * 100.0 : 0.0;

        return ActivityAttendanceSummaryResponse.builder()
                .activityId(activityId)
                .activityTitle(activity.getTitle())
                .totalLogged(total)
                .attended(attended)
                .absent(absent)
                .declined(declined)
                .attendanceRate(rate)
                .build();
    }

    // ── Low-participation detection ───────────────────────────────────────────

    /**
     * Returns all active residents whose attendance rate over the lookback window
     * falls below the given threshold.
     *
     * @param thresholdPercent flag residents below this rate (use 0 to apply default of 50)
     * @param lookbackDays     rolling window in days (use 0 to apply default of 30)
     */
    @Transactional(readOnly = true)
    public List<ResidentParticipationResponse> getLowParticipationResidents(int thresholdPercent,
                                                                             int lookbackDays) {
        int threshold = thresholdPercent > 0 ? thresholdPercent : DEFAULT_THRESHOLD_PERCENT;
        int lookback  = lookbackDays > 0 ? lookbackDays : DEFAULT_LOOKBACK_DAYS;

        LocalDateTime since = LocalDateTime.now().minusDays(lookback);

        return residentRepository.findAll().stream()
                .filter(Resident::isActive)
                .map(resident -> {
                    List<AttendanceLog> logs = attendanceLogRepository
                            .findByResidentIdAndLoggedAtBetween(resident.getId(), since, LocalDateTime.now());

                    long total    = logs.size();
                    long attended = logs.stream()
                            .filter(l -> l.getStatus() == AttendanceStatus.ATTENDED)
                            .count();
                    double rate   = total > 0 ? (double) attended / total * 100.0 : 0.0;

                    return ResidentParticipationResponse.builder()
                            .residentId(resident.getId())
                            .residentName(resident.getFirstName() + " " + resident.getLastName())
                            .totalActivities(total)
                            .attended(attended)
                            .participationRate(rate)
                            .flaggedAsLow(rate < threshold)
                            .build();
                })
                .filter(ResidentParticipationResponse::isFlaggedAsLow)
                .toList();
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private AttendanceLogResponse toResponse(AttendanceLog log) {
        return AttendanceLogResponse.builder()
                .id(log.getId())
                .activityId(log.getActivity().getId())
                .activityTitle(log.getActivity().getTitle())
                .residentId(log.getResident().getId())
                .residentName(log.getResident().getFirstName() + " " + log.getResident().getLastName())
                .status(log.getStatus())
                .assistanceLevel(log.getAssistanceLevel())
                .assistanceNotes(log.getAssistanceNotes())
                .loggedAt(log.getLoggedAt())
                .loggedByName(log.getLoggedBy().getEmail())
                .build();
    }
}
