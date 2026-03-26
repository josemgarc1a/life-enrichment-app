package com.lifeenrichment.controller;

import com.lifeenrichment.dto.request.CreateActivityRequest;
import com.lifeenrichment.dto.request.EnrollResidentRequest;
import com.lifeenrichment.dto.request.UpdateActivityRequest;
import com.lifeenrichment.dto.response.ActivityResponse;
import com.lifeenrichment.dto.response.CalendarEventResponse;
import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for activity scheduling — create, read, update, cancel, delete, and enrollment.
 *
 * <p>Write operations (create, update, cancel, delete, enrollment management) require
 * the {@code DIRECTOR} role. Read operations (get, list, calendar) require at least {@code STAFF}.
 * All business logic is delegated to {@link ActivityService}.
 */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Tag(name = "Activities", description = "Manage activity scheduling, categories, and resident enrollment")
public class ActivityController {

    private final ActivityService activityService;
    private final UserRepository userRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Operation(summary = "Create a new activity")
    @ApiResponse(responseCode = "201", description = "Activity created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping
    public ResponseEntity<ActivityResponse> createActivity(
            @Valid @RequestBody CreateActivityRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        UUID creatorId = resolveUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(activityService.createActivity(request, creatorId));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Operation(summary = "Get an activity by ID")
    @ApiResponse(responseCode = "200", description = "Activity found")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Activity not found or deleted")
    @PreAuthorize("hasAnyRole('DIRECTOR','STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ActivityResponse> getActivity(@PathVariable UUID id) {
        return ResponseEntity.ok(activityService.getActivity(id));
    }

    @Operation(summary = "List activities with optional category and status filters")
    @ApiResponse(responseCode = "200", description = "Paginated activity list")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasAnyRole('DIRECTOR','STAFF')")
    @GetMapping
    public ResponseEntity<Page<ActivityResponse>> listActivities(
            @RequestParam(required = false) Activity.Category category,
            @RequestParam(required = false) Activity.Status status,
            Pageable pageable) {
        return ResponseEntity.ok(activityService.listActivities(category, status, pageable));
    }

    @Operation(summary = "Get calendar events for a date range")
    @ApiResponse(responseCode = "200", description = "Calendar event list")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasAnyRole('DIRECTOR','STAFF')")
    @GetMapping("/calendar")
    public ResponseEntity<List<CalendarEventResponse>> getCalendarEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(activityService.getCalendarEvents(startDate, endDate));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Operation(summary = "Update an activity (partial update — only supplied fields are changed)")
    @ApiResponse(responseCode = "200", description = "Activity updated")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PutMapping("/{id}")
    public ResponseEntity<ActivityResponse> updateActivity(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateActivityRequest request) {
        return ResponseEntity.ok(activityService.updateActivity(id, request));
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Operation(summary = "Cancel an activity (remains visible on calendar as CANCELLED)")
    @ApiResponse(responseCode = "200", description = "Activity cancelled")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ActivityResponse> cancelActivity(@PathVariable UUID id) {
        return ResponseEntity.ok(activityService.cancelActivity(id));
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @Operation(summary = "Soft-delete an activity (hidden from all views)")
    @ApiResponse(responseCode = "204", description = "Activity deleted")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivity(@PathVariable UUID id) {
        activityService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }

    // ── Enrollment ────────────────────────────────────────────────────────────

    @Operation(summary = "Enroll a resident in an activity")
    @ApiResponse(responseCode = "200", description = "Resident enrolled, updated roster returned")
    @ApiResponse(responseCode = "400", description = "Resident already enrolled or capacity exceeded")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Activity or resident not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping("/{id}/enrollments")
    public ResponseEntity<ActivityResponse> enrollResident(
            @PathVariable UUID id,
            @Valid @RequestBody EnrollResidentRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        UUID enrolledBy = resolveUserId(principal);
        return ResponseEntity.ok(activityService.enrollResident(id, request, enrolledBy));
    }

    @Operation(summary = "Unenroll a resident from an activity")
    @ApiResponse(responseCode = "200", description = "Resident unenrolled, updated roster returned")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Activity or enrollment not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @DeleteMapping("/{id}/enrollments/{residentId}")
    public ResponseEntity<ActivityResponse> unenrollResident(
            @PathVariable UUID id,
            @PathVariable UUID residentId) {
        return ResponseEntity.ok(activityService.unenrollResident(id, residentId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the authenticated user's UUID from their email (JWT subject).
     *
     * @throws ResourceNotFoundException if no user account exists for the authenticated email
     */
    private UUID resolveUserId(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getUsername()));
    }
}
