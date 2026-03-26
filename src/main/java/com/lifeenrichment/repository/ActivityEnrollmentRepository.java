package com.lifeenrichment.repository;

import com.lifeenrichment.entity.ActivityEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ActivityEnrollment} entities.
 *
 * <p>Used by {@code ActivityService} to enforce capacity limits, detect duplicate
 * enrollments, and retrieve the roster for a given activity or resident.
 */
@Repository
public interface ActivityEnrollmentRepository extends JpaRepository<ActivityEnrollment, UUID> {

    /** Returns all enrollments for the given activity (the full roster). */
    List<ActivityEnrollment> findByActivityId(UUID activityId);

    /** Returns all activities a resident is enrolled in. */
    List<ActivityEnrollment> findByResidentId(UUID residentId);

    /** Counts enrollments for a given activity — used to enforce capacity. */
    long countByActivityId(UUID activityId);

    /** Duplicate-check guard: true if the resident is already enrolled in the activity. */
    boolean existsByActivityIdAndResidentId(UUID activityId, UUID residentId);

    /** Removes the enrollment record when a resident is unenrolled from an activity. */
    void deleteByActivityIdAndResidentId(UUID activityId, UUID residentId);
}
