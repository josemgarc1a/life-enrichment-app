package com.lifeenrichment.controller;

import com.lifeenrichment.dto.request.LogAttendanceRequest;
import com.lifeenrichment.dto.response.ActivityAttendanceSummaryResponse;
import com.lifeenrichment.dto.response.AttendanceLogResponse;
import com.lifeenrichment.dto.response.ResidentParticipationResponse;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for attendance logging and participation reporting.
 *
 * <p>All endpoints require authentication. Write operations and resident history are accessible
 * to both {@code STAFF} and {@code DIRECTOR}. The low-participation report is restricted to
 * {@code DIRECTOR} only. Business logic is fully delegated to {@link AttendanceService}.
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Log resident attendance and query participation reports")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;

    // ── Log attendance ────────────────────────────────────────────────────────

    @Operation(summary = "Log or update attendance for a resident at an activity")
    @ApiResponse(responseCode = "200", description = "Attendance logged or updated")
    @ApiResponse(responseCode = "400", description = "Validation error or resident not enrolled")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Activity, resident, or user not found")
    @PreAuthorize("hasAnyRole('DIRECTOR','STAFF')")
    @PostMapping
    public ResponseEntity<AttendanceLogResponse> logAttendance(
            @Valid @RequestBody LogAttendanceRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        UUID loggedByUserId = resolveUserId(principal);
        return ResponseEntity.ok(attendanceService.logAttendance(request, loggedByUserId));
    }

    // ── Resident history ──────────────────────────────────────────────────────

    @Operation(summary = "Get attendance history for a resident (optional date range filter)")
    @ApiResponse(responseCode = "200", description = "Attendance history returned")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Resident not found")
    @PreAuthorize("hasAnyRole('DIRECTOR','STAFF')")
    @GetMapping("/resident/{residentId}")
    public ResponseEntity<List<AttendanceLogResponse>> getResidentHistory(
            @PathVariable UUID residentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(attendanceService.getResidentHistory(residentId, from, to));
    }

    // ── Activity summary ──────────────────────────────────────────────────────

    @Operation(summary = "Get attendance summary (counts by status and overall rate) for an activity")
    @ApiResponse(responseCode = "200", description = "Attendance summary returned")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    @PreAuthorize("hasAnyRole('DIRECTOR','STAFF')")
    @GetMapping("/activity/{activityId}/summary")
    public ResponseEntity<ActivityAttendanceSummaryResponse> getActivitySummary(
            @PathVariable UUID activityId) {
        return ResponseEntity.ok(attendanceService.getActivitySummary(activityId));
    }

    // ── Low-participation report ──────────────────────────────────────────────

    @Operation(summary = "Get residents flagged for low participation (DIRECTOR only)")
    @ApiResponse(responseCode = "200", description = "Low-participation resident list returned")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    @ApiResponse(responseCode = "403", description = "Access denied — DIRECTOR role required")
    @PreAuthorize("hasRole('DIRECTOR')")
    @GetMapping("/participation/low")
    public ResponseEntity<List<ResidentParticipationResponse>> getLowParticipation(
            @RequestParam(defaultValue = "0") int threshold,
            @RequestParam(defaultValue = "0") int lookbackDays) {
        return ResponseEntity.ok(attendanceService.getLowParticipationResidents(threshold, lookbackDays));
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
