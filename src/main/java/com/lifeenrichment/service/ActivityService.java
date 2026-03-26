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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing activities and resident enrollments.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li><strong>Soft delete</strong> — activities set {@code deletedAt}; historical data is preserved.</li>
 *   <li><strong>Cancel vs delete</strong> — cancelling sets status to CANCELLED (visible on calendar);
 *       deleting hides the activity from all views via soft-delete.</li>
 *   <li><strong>Capacity enforcement</strong> — checked at enroll time by counting existing enrollments.</li>
 *   <li><strong>Duplicate guard</strong> — {@code existsByActivityIdAndResidentId} prevents double-enrollment.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityEnrollmentRepository enrollmentRepository;
    private final ResidentRepository residentRepository;
    private final UserRepository userRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates and persists a new activity.
     *
     * @param request  the activity details
     * @param creatorId the ID of the authenticated user creating this activity
     * @return the full activity response including generated ID
     */
    @Transactional
    public ActivityResponse createActivity(CreateActivityRequest request, UUID creatorId) {
        log.info("Creating activity '{}' by user {}", request.getTitle(), creatorId);

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", creatorId));

        Activity activity = Activity.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .location(request.getLocation())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .capacity(request.getCapacity())
                .recurrenceRule(request.getRecurrenceRule())
                .createdBy(creator)
                .build();

        Activity saved = activityRepository.save(activity);
        log.info("Activity created with id: {}", saved.getId());
        return toResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single non-deleted activity by ID.
     *
     * @throws ResourceNotFoundException if no active activity exists with that ID
     */
    @Transactional(readOnly = true)
    public ActivityResponse getActivity(UUID id) {
        Activity activity = requireActive(id);
        return toResponse(activity);
    }

    /**
     * Returns a paginated list of non-deleted activities, optionally filtered by
     * category and/or status. Pass {@code null} for either parameter to skip that filter.
     */
    @Transactional(readOnly = true)
    public Page<ActivityResponse> listActivities(Activity.Category category,
                                                 Activity.Status status,
                                                 Pageable pageable) {
        Page<Activity> page;
        if (category != null && status != null) {
            page = activityRepository.findByDeletedAtIsNullAndCategoryAndStatus(category, status, pageable);
        } else if (category != null) {
            page = activityRepository.findByDeletedAtIsNullAndCategory(category, pageable);
        } else if (status != null) {
            page = activityRepository.findByDeletedAtIsNullAndStatus(status, pageable);
        } else {
            page = activityRepository.findByDeletedAtIsNull(pageable);
        }
        return page.map(this::toResponse);
    }

    /**
     * Returns all non-deleted activities whose start time falls within the given range,
     * formatted as lightweight calendar events.
     *
     * @param from inclusive start of the date range
     * @param to   inclusive end of the date range
     */
    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getCalendarEvents(LocalDateTime from, LocalDateTime to) {
        return activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(from, to)
                .stream()
                .map(this::toCalendarEvent)
                .toList();
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Applies a partial update to a non-deleted activity.
     * Only non-null fields in the request overwrite existing values.
     *
     * @throws ResourceNotFoundException if no active activity exists with that ID
     */
    @Transactional
    public ActivityResponse updateActivity(UUID id, UpdateActivityRequest request) {
        log.info("Updating activity id: {}", id);

        Activity activity = requireActive(id);

        if (request.getTitle() != null)         activity.setTitle(request.getTitle());
        if (request.getDescription() != null)   activity.setDescription(request.getDescription());
        if (request.getCategory() != null)      activity.setCategory(request.getCategory());
        if (request.getLocation() != null)      activity.setLocation(request.getLocation());
        if (request.getStartTime() != null)     activity.setStartTime(request.getStartTime());
        if (request.getEndTime() != null)       activity.setEndTime(request.getEndTime());
        if (request.getCapacity() != null)      activity.setCapacity(request.getCapacity());
        if (request.getRecurrenceRule() != null) activity.setRecurrenceRule(request.getRecurrenceRule());

        Activity saved = activityRepository.save(activity);
        log.info("Activity updated: {}", id);
        return toResponse(saved);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    /**
     * Sets the activity status to {@code CANCELLED}. The activity remains visible
     * on the calendar but is visually distinguished as cancelled.
     *
     * @throws ResourceNotFoundException if no active activity exists with that ID
     */
    @Transactional
    public ActivityResponse cancelActivity(UUID id) {
        log.info("Cancelling activity id: {}", id);

        Activity activity = requireActive(id);
        activity.setStatus(Activity.Status.CANCELLED);

        Activity saved = activityRepository.save(activity);
        log.info("Activity cancelled: {}", id);
        return toResponse(saved);
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    /**
     * Soft-deletes an activity by setting {@code deletedAt}.
     * The activity is hidden from all queries after deletion.
     *
     * @throws ResourceNotFoundException if no active activity exists with that ID
     */
    @Transactional
    public void deleteActivity(UUID id) {
        log.info("Soft-deleting activity id: {}", id);

        Activity activity = requireActive(id);
        activity.setDeletedAt(LocalDateTime.now());
        activityRepository.save(activity);

        log.info("Activity soft-deleted: {}", id);
    }

    // ── Enrollment ────────────────────────────────────────────────────────────

    /**
     * Enrolls a resident into an activity, enforcing capacity and duplicate checks.
     *
     * @param activityId the activity to enroll into
     * @param request    contains the resident's UUID
     * @param enrolledBy the UUID of the staff member performing the enrollment
     * @throws ResourceNotFoundException if the activity or resident does not exist
     * @throws BusinessException         if the resident is already enrolled or capacity is full
     */
    @Transactional
    public ActivityResponse enrollResident(UUID activityId,
                                           EnrollResidentRequest request,
                                           UUID enrolledBy) {
        Activity activity = requireActive(activityId);
        UUID residentId = request.getResidentId();

        Resident resident = residentRepository.findById(residentId)
                .orElseThrow(() -> new ResourceNotFoundException("Resident", residentId));

        if (enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId)) {
            throw new BusinessException("Resident " + residentId + " is already enrolled in activity " + activityId);
        }

        long currentCount = enrollmentRepository.countByActivityId(activityId);
        if (currentCount >= activity.getCapacity()) {
            throw new BusinessException("Activity " + activityId + " is at full capacity ("
                    + activity.getCapacity() + " enrollments)");
        }

        User enrolledByUser = userRepository.findById(enrolledBy)
                .orElseThrow(() -> new ResourceNotFoundException("User", enrolledBy));

        enrollmentRepository.save(ActivityEnrollment.builder()
                .activity(activity)
                .resident(resident)
                .enrolledBy(enrolledByUser)
                .build());

        log.info("Resident {} enrolled in activity {} by user {}", residentId, activityId, enrolledBy);
        return toResponse(activityRepository.findById(activityId).orElseThrow());
    }

    /**
     * Removes a resident's enrollment from an activity.
     *
     * @throws ResourceNotFoundException if the activity or enrollment does not exist
     */
    @Transactional
    public ActivityResponse unenrollResident(UUID activityId, UUID residentId) {
        requireActive(activityId);

        if (!enrollmentRepository.existsByActivityIdAndResidentId(activityId, residentId)) {
            throw new ResourceNotFoundException("ActivityEnrollment",
                    "activityId=" + activityId + " residentId=" + residentId);
        }

        enrollmentRepository.deleteByActivityIdAndResidentId(activityId, residentId);
        log.info("Resident {} unenrolled from activity {}", residentId, activityId);
        return toResponse(activityRepository.findById(activityId).orElseThrow());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Loads a non-deleted activity or throws {@link ResourceNotFoundException}.
     */
    private Activity requireActive(UUID id) {
        return activityRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ActivityResponse toResponse(Activity activity) {
        List<ActivityEnrollment> enrollments = enrollmentRepository.findByActivityId(activity.getId());

        return ActivityResponse.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .description(activity.getDescription())
                .category(activity.getCategory())
                .location(activity.getLocation())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .capacity(activity.getCapacity())
                .recurrenceRule(activity.getRecurrenceRule())
                .status(activity.getStatus())
                .createdById(activity.getCreatedBy() != null ? activity.getCreatedBy().getId() : null)
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .enrollmentCount(enrollments.size())
                .enrolledResidentIds(enrollments.stream()
                        .map(e -> e.getResident().getId())
                        .toList())
                .build();
    }

    private CalendarEventResponse toCalendarEvent(Activity activity) {
        return CalendarEventResponse.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .start(activity.getStartTime())
                .end(activity.getEndTime())
                .category(activity.getCategory())
                .status(activity.getStatus())
                .location(activity.getLocation())
                .capacity(activity.getCapacity())
                .enrollmentCount(enrollmentRepository.countByActivityId(activity.getId()))
                .build();
    }
}
