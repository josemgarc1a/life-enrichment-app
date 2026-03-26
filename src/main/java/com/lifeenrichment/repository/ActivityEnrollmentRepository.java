package com.lifeenrichment.repository;

import com.lifeenrichment.entity.ActivityEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ActivityEnrollment} entities.
 */
@Repository
public interface ActivityEnrollmentRepository extends JpaRepository<ActivityEnrollment, UUID> {

    List<ActivityEnrollment> findByActivityId(UUID activityId);

    List<ActivityEnrollment> findByResidentId(UUID residentId);

    long countByActivityId(UUID activityId);

    boolean existsByActivityIdAndResidentId(UUID activityId, UUID residentId);

    void deleteByActivityIdAndResidentId(UUID activityId, UUID residentId);
}
