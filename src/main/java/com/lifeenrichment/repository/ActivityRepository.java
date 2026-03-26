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
 * unless the method name explicitly indicates otherwise. Use {@link #findById} only when
 * you need to retrieve potentially deleted records (e.g. for audit purposes).
 */
@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    /** Returns a non-deleted activity by ID, or empty if deleted or not found. */
    Optional<Activity> findByIdAndDeletedAtIsNull(UUID id);

    /** Returns all non-deleted activities with the given status, newest first. */
    List<Activity> findByStatusAndDeletedAtIsNull(Activity.Status status);

    /** Returns all non-deleted activities whose start_time falls within the given range. */
    List<Activity> findByStartTimeBetweenAndDeletedAtIsNull(LocalDateTime from, LocalDateTime to);

    /** Returns all non-deleted activities in the given category. */
    List<Activity> findByCategoryAndDeletedAtIsNull(Activity.Category category);

    /**
     * Pageable listing of non-deleted activities, optionally filtered by category and/or status.
     * Pass {@code null} for either parameter to skip that filter.
     */
    Page<Activity> findByDeletedAtIsNullAndCategoryAndStatus(
            Activity.Category category, Activity.Status status, Pageable pageable);

    /** Pageable listing of non-deleted activities filtered by category only. */
    Page<Activity> findByDeletedAtIsNullAndCategory(Activity.Category category, Pageable pageable);

    /** Pageable listing of non-deleted activities filtered by status only. */
    Page<Activity> findByDeletedAtIsNullAndStatus(Activity.Status status, Pageable pageable);

    /** Pageable listing of all non-deleted activities with no additional filters. */
    Page<Activity> findByDeletedAtIsNull(Pageable pageable);
}
