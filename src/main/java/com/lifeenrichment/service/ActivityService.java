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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

        String rrule = request.isRecurring()
                ? buildRrule(request.getDayOfWeek())
                : null;

        Activity activity = Activity.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .location(request.getLocation())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .capacity(request.getCapacity())
                .recurrenceRule(rrule)
                .createdBy(creator)
                .build();

        Activity saved = activityRepository.save(activity);
        log.info("Activity created with id: {}", saved.getId());

        if (request.isRecurring()) {
            List<Activity> occurrences = expandSeries(saved, RECURRENCE_WINDOW_WEEKS);
            log.info("Generated {} occurrences for recurring activity {}", occurrences.size(), saved.getId());
            return toResponseWithOccurrenceCount(saved, occurrences.size());
        }

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

    // ── Series management ─────────────────────────────────────────────────────

    /**
     * Cancels an entire recurring series: soft-deletes all future occurrences and
     * marks the template row as {@code CANCELLED}.
     *
     * @throws ResourceNotFoundException if no active template exists with that ID
     * @throws BusinessException         if the activity is not a recurring series template
     */
    @Transactional
    public ActivityResponse cancelSeries(UUID templateId) {
        log.info("Cancelling series for template activity {}", templateId);

        Activity template = requireActive(templateId);
        if (template.getRecurrenceRule() == null) {
            throw new BusinessException("Activity " + templateId + " is not a recurring series template");
        }

        List<Activity> futureOccurrences = activityRepository
                .findBySeriesIdAndStartTimeAfterAndDeletedAtIsNull(templateId, LocalDateTime.now());

        futureOccurrences.forEach(o -> o.setDeletedAt(LocalDateTime.now()));
        activityRepository.saveAll(futureOccurrences);

        template.setStatus(Activity.Status.CANCELLED);
        Activity saved = activityRepository.save(template);

        log.info("Cancelled series {}: soft-deleted {} future occurrences", templateId, futureOccurrences.size());
        return toResponse(saved);
    }

    /**
     * Applies title, location, and/or capacity changes to all future occurrences of a series.
     * Does not alter occurrence start/end times.
     *
     * @throws ResourceNotFoundException if no active template exists with that ID
     * @throws BusinessException         if the activity is not a recurring series template
     */
    @Transactional
    public ActivityResponse updateSeries(UUID templateId, UpdateActivityRequest request) {
        log.info("Updating series for template activity {}", templateId);

        Activity template = requireActive(templateId);
        if (template.getRecurrenceRule() == null) {
            throw new BusinessException("Activity " + templateId + " is not a recurring series template");
        }

        // Update the template itself
        if (request.getTitle() != null)       template.setTitle(request.getTitle());
        if (request.getDescription() != null) template.setDescription(request.getDescription());
        if (request.getCategory() != null)    template.setCategory(request.getCategory());
        if (request.getLocation() != null)    template.setLocation(request.getLocation());
        if (request.getCapacity() != null)    template.setCapacity(request.getCapacity());
        activityRepository.save(template);

        // Propagate to all future occurrences (title, description, location, category, capacity only)
        List<Activity> futureOccurrences = activityRepository
                .findBySeriesIdAndStartTimeAfterAndDeletedAtIsNull(templateId, LocalDateTime.now());

        futureOccurrences.forEach(o -> {
            if (request.getTitle() != null)       o.setTitle(request.getTitle());
            if (request.getDescription() != null) o.setDescription(request.getDescription());
            if (request.getCategory() != null)    o.setCategory(request.getCategory());
            if (request.getLocation() != null)    o.setLocation(request.getLocation());
            if (request.getCapacity() != null)    o.setCapacity(request.getCapacity());
        });
        activityRepository.saveAll(futureOccurrences);

        log.info("Updated series {}: propagated changes to {} future occurrences", templateId, futureOccurrences.size());
        return toResponse(activityRepository.findById(templateId).orElseThrow());
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

    // ── Recurrence helpers ────────────────────────────────────────────────────

    public static final int RECURRENCE_WINDOW_WEEKS = 8;

    private static final Map<String, DayOfWeek> BYDAY_MAP = Map.of(
            "MO", DayOfWeek.MONDAY,
            "TU", DayOfWeek.TUESDAY,
            "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY,
            "FR", DayOfWeek.FRIDAY,
            "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY
    );

    private static final Map<String, String> DAY_TO_BYDAY = Map.of(
            "MONDAY", "MO", "TUESDAY", "TU", "WEDNESDAY", "WE",
            "THURSDAY", "TH", "FRIDAY", "FR", "SATURDAY", "SA", "SUNDAY", "SU"
    );

    /**
     * Builds an iCal RRULE string from a plain day-of-week name.
     *
     * @param dayOfWeek e.g. {@code "THURSDAY"}
     * @return e.g. {@code "FREQ=WEEKLY;BYDAY=TH"}
     * @throws BusinessException if the day name is not recognised
     */
    String buildRrule(String dayOfWeek) {
        String byday = Optional.ofNullable(DAY_TO_BYDAY.get(dayOfWeek.toUpperCase()))
                .orElseThrow(() -> new BusinessException("Invalid dayOfWeek value: " + dayOfWeek
                        + ". Expected one of: " + DAY_TO_BYDAY.keySet()));
        return "FREQ=WEEKLY;BYDAY=" + byday;
    }

    /**
     * Parses the {@code BYDAY} component of an RRULE string and returns the corresponding
     * {@link DayOfWeek}.
     *
     * @param rrule e.g. {@code "FREQ=WEEKLY;BYDAY=TH"}
     * @throws BusinessException if BYDAY is missing or unrecognised
     */
    DayOfWeek parseDayOfWeek(String rrule) {
        String byday = java.util.Arrays.stream(rrule.split(";"))
                .filter(p -> p.startsWith("BYDAY="))
                .findFirst()
                .map(p -> p.substring(6))
                .orElseThrow(() -> new BusinessException("Invalid RRULE: missing BYDAY component — " + rrule));
        return Optional.ofNullable(BYDAY_MAP.get(byday))
                .orElseThrow(() -> new BusinessException("Unrecognised BYDAY value: " + byday));
    }

    /**
     * Generates occurrence {@link Activity} rows for a series template, filling the
     * rolling window up to {@code weeks} weeks from now. Idempotent — existing occurrences
     * at any given start time are skipped.
     *
     * @param template the series template (must have {@code recurrenceRule} set)
     * @param weeks    number of weeks ahead to fill
     * @return the list of newly created occurrence rows (may be empty if all dates already exist)
     */
    public List<Activity> expandSeries(Activity template, int weeks) {
        DayOfWeek targetDay = parseDayOfWeek(template.getRecurrenceRule());
        Duration occurrenceDuration = Duration.between(template.getStartTime(), template.getEndTime());

        LocalDateTime windowEnd = LocalDateTime.now().plusWeeks(weeks);

        // Find the first occurrence of targetDay on or after today
        LocalDate nextDate = LocalDate.now();
        while (nextDate.getDayOfWeek() != targetDay) {
            nextDate = nextDate.plusDays(1);
        }

        List<Activity> toSave = new ArrayList<>();
        LocalDateTime occurrenceStart = LocalDateTime.of(nextDate, template.getStartTime().toLocalTime());

        while (occurrenceStart.isBefore(windowEnd)) {
            if (!activityRepository.existsBySeriesIdAndStartTime(template.getId(), occurrenceStart)) {
                toSave.add(Activity.builder()
                        .title(template.getTitle())
                        .description(template.getDescription())
                        .category(template.getCategory())
                        .location(template.getLocation())
                        .capacity(template.getCapacity())
                        .startTime(occurrenceStart)
                        .endTime(occurrenceStart.plus(occurrenceDuration))
                        .seriesId(template.getId())
                        .createdBy(template.getCreatedBy())
                        .build());
            }
            occurrenceStart = occurrenceStart.plusWeeks(1);
        }

        return activityRepository.saveAll(toSave);
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

    private ActivityResponse toResponseWithOccurrenceCount(Activity activity, int occurrenceCount) {
        ActivityResponse base = toResponse(activity);
        return ActivityResponse.builder()
                .id(base.getId())
                .title(base.getTitle())
                .description(base.getDescription())
                .category(base.getCategory())
                .location(base.getLocation())
                .startTime(base.getStartTime())
                .endTime(base.getEndTime())
                .capacity(base.getCapacity())
                .recurrenceRule(base.getRecurrenceRule())
                .status(base.getStatus())
                .seriesId(base.getSeriesId())
                .createdById(base.getCreatedById())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .occurrenceCount(occurrenceCount)
                .enrollmentCount(base.getEnrollmentCount())
                .enrolledResidentIds(base.getEnrolledResidentIds())
                .build();
    }

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
                .seriesId(activity.getSeriesId())
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
