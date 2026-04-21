package com.lifeenrichment.repository;

import com.lifeenrichment.entity.AttendanceLog;
import com.lifeenrichment.entity.AttendanceLog.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AttendanceLog} entities.
 * Provides queries for per-resident history, per-activity summaries,
 * and participation-rate calculations.
 */
@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, UUID> {

    /**
     * Returns the full attendance history for a resident, most recent first.
     */
    List<AttendanceLog> findByResidentIdOrderByLoggedAtDesc(UUID residentId);

    /**
     * Returns attendance logs for a resident within an inclusive date range.
     */
    List<AttendanceLog> findByResidentIdAndLoggedAtBetween(UUID residentId,
                                                           LocalDateTime from,
                                                           LocalDateTime to);

    /**
     * Returns all attendance logs for a given activity.
     */
    List<AttendanceLog> findByActivityId(UUID activityId);

    /**
     * Returns attendance logs for a given activity filtered by status.
     */
    List<AttendanceLog> findByActivityIdAndStatus(UUID activityId, AttendanceStatus status);

    /**
     * Counts how many times a resident attended (or had any given status) after a cutoff date.
     * Used to calculate participation rates over a rolling window.
     */
    long countByResidentIdAndStatusAndLoggedAtAfter(UUID residentId,
                                                    AttendanceStatus status,
                                                    LocalDateTime since);

    /**
     * Returns the existing log for a specific activity + resident combination, if any.
     * Used to enforce the one-log-per-activity-per-resident rule.
     */
    Optional<AttendanceLog> findByActivityIdAndResidentId(UUID activityId, UUID residentId);

    /**
     * Returns true if an attendance log already exists for this activity + resident.
     */
    boolean existsByActivityIdAndResidentId(UUID activityId, UUID residentId);
}
