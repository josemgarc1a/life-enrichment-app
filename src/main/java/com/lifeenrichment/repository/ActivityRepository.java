package com.lifeenrichment.repository;

import com.lifeenrichment.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Activity} entities.
 *
 * <p>All query methods filter out soft-deleted records ({@code deleted_at IS NULL})
 * unless the method name explicitly indicates otherwise.
 */
@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findByIdAndDeletedAtIsNull(UUID id);

    List<Activity> findByStatusAndDeletedAtIsNull(Activity.Status status);

    List<Activity> findByStartTimeBetweenAndDeletedAtIsNull(LocalDateTime from, LocalDateTime to);

    List<Activity> findByCategoryAndDeletedAtIsNull(Activity.Category category);

    Page<Activity> findByDeletedAtIsNullAndCategoryAndStatus(
            Activity.Category category, Activity.Status status, Pageable pageable);

    Page<Activity> findByDeletedAtIsNullAndCategory(Activity.Category category, Pageable pageable);

    Page<Activity> findByDeletedAtIsNullAndStatus(Activity.Status status, Pageable pageable);

    Page<Activity> findByDeletedAtIsNull(Pageable pageable);

    /** All active template activities (have a recurrence rule, not deleted, not cancelled). */
    List<Activity> findByRecurrenceRuleIsNotNullAndDeletedAtIsNullAndStatus(Activity.Status status);

    /** All non-deleted occurrences that belong to a series. */
    List<Activity> findBySeriesIdAndDeletedAtIsNull(UUID seriesId);

    /** Future non-deleted occurrences belonging to a series — used for cancel/update series. */
    List<Activity> findBySeriesIdAndStartTimeAfterAndDeletedAtIsNull(UUID seriesId, LocalDateTime after);

    /** Idempotency check — true if an occurrence for this series already exists at this exact start time. */
    boolean existsBySeriesIdAndStartTime(UUID seriesId, LocalDateTime startTime);
}
