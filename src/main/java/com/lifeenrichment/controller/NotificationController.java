package com.lifeenrichment.controller;

import com.lifeenrichment.dto.request.BroadcastRequest;
import com.lifeenrichment.dto.request.UpdateNotificationPreferenceRequest;
import com.lifeenrichment.dto.response.NotificationLogResponse;
import com.lifeenrichment.dto.response.NotificationPreferenceResponse;
import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationLog.DeliveryStatus;
import com.lifeenrichment.entity.NotificationPreference;
import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.repository.NotificationLogRepository;
import com.lifeenrichment.repository.NotificationPreferenceRepository;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for notification preferences, delivery logs, and Director broadcast.
 *
 * <p>Authenticated users manage their own channel preferences via the preferences endpoints.
 * Directors can view all delivery logs (optionally filtered by user and/or status) and
 * send manual broadcasts to all users or a targeted subset.
 *
 * <p>All business logic for broadcast delivery is delegated to {@link NotificationService}
 * which runs asynchronously so the POST /broadcast endpoint returns immediately with
 * {@code 202 Accepted}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification preferences, logs, and broadcast")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final UserRepository userRepository;

    // ── GET /preferences ─────────────────────────────────────────────────────

    /**
     * Returns all notification-channel preferences configured by the calling user.
     * Only preference records that have been explicitly set are returned; types with
     * no row use system defaults (email on, SMS off, push off).
     */
    @Operation(summary = "Get all notification channel preferences for the calling user")
    @ApiResponse(responseCode = "200", description = "Preferences returned")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/preferences")
    public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = resolveUserId(principal);
        log.debug("getPreferences: userId={}", userId);

        List<NotificationPreferenceResponse> responses = notificationPreferenceRepository
                .findByUserId(userId)
                .stream()
                .map(this::toPreferenceResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ── PUT /preferences/{type} ───────────────────────────────────────────────

    /**
     * Upserts the notification-channel preference for a single notification type for the
     * calling user. Creates a new preference record if none exists; updates the existing
     * one if it does. Returns the saved state.
     */
    @Operation(summary = "Upsert a notification channel preference for the calling user")
    @ApiResponse(responseCode = "200", description = "Preference saved and returned")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/preferences/{type}")
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(
            @PathVariable NotificationType type,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = resolveUserId(principal);
        log.debug("updatePreference: userId={}, type={}", userId, type);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        NotificationPreference preference = notificationPreferenceRepository
                .findByUserIdAndNotificationType(userId, type)
                .orElseGet(() -> NotificationPreference.builder()
                        .user(user)
                        .notificationType(type)
                        .build());

        preference.setEmailEnabled(request.isEmailEnabled());
        preference.setSmsEnabled(request.isSmsEnabled());
        preference.setPushEnabled(request.isPushEnabled());

        NotificationPreference saved = notificationPreferenceRepository.save(preference);
        return ResponseEntity.ok(toPreferenceResponse(saved));
    }

    // ── POST /broadcast ───────────────────────────────────────────────────────

    /**
     * Triggers an asynchronous broadcast notification to the specified target users.
     * When {@code targetUserIds} is null or empty the broadcast is sent to every user
     * currently registered in the system. Returns immediately with {@code 202 Accepted}.
     */
    @Operation(summary = "Send a manual broadcast notification to target users (DIRECTOR only)")
    @ApiResponse(responseCode = "202", description = "Broadcast accepted for async delivery")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    @ApiResponse(responseCode = "403", description = "Access denied — DIRECTOR role required")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping("/broadcast")
    public ResponseEntity<Void> broadcast(
            @Valid @RequestBody BroadcastRequest request) {
        List<UUID> targetUserIds = request.getTargetUserIds();

        if (targetUserIds == null || targetUserIds.isEmpty()) {
            targetUserIds = userRepository.findAll()
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            log.debug("broadcast: no targetUserIds specified, broadcasting to {} users", targetUserIds.size());
        } else {
            log.debug("broadcast: targeting {} specific users", targetUserIds.size());
        }

        notificationService.sendBroadcast(request.getMessage(), targetUserIds);
        return ResponseEntity.accepted().build();
    }

    // ── GET /logs ─────────────────────────────────────────────────────────────

    /**
     * Returns notification delivery logs, optionally filtered by recipient user and/or
     * delivery status. When neither parameter is provided all logs are returned. Access
     * is restricted to the {@code DIRECTOR} role.
     */
    @Operation(summary = "List notification delivery logs with optional userId and status filters (DIRECTOR only)")
    @ApiResponse(responseCode = "200", description = "Notification logs returned")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    @ApiResponse(responseCode = "403", description = "Access denied — DIRECTOR role required")
    @PreAuthorize("hasRole('DIRECTOR')")
    @GetMapping("/logs")
    public ResponseEntity<List<NotificationLogResponse>> getLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) DeliveryStatus status) {
        log.debug("getLogs: userId={}, status={}", userId, status);

        List<NotificationLog> logs;

        if (userId != null && status != null) {
            logs = notificationLogRepository.findByUserIdOrderBySentAtDesc(userId)
                    .stream()
                    .filter(l -> l.getStatus() == status)
                    .collect(Collectors.toList());
        } else if (userId != null) {
            logs = notificationLogRepository.findByUserIdOrderBySentAtDesc(userId);
        } else if (status != null) {
            logs = notificationLogRepository.findByStatus(status);
        } else {
            logs = notificationLogRepository.findAll();
        }

        List<NotificationLogResponse> responses = logs.stream()
                .map(this::toLogResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
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

    /** Maps a {@link NotificationPreference} entity to its response DTO. */
    private NotificationPreferenceResponse toPreferenceResponse(NotificationPreference preference) {
        return NotificationPreferenceResponse.builder()
                .id(preference.getId())
                .notificationType(preference.getNotificationType())
                .emailEnabled(preference.isEmailEnabled())
                .smsEnabled(preference.isSmsEnabled())
                .pushEnabled(preference.isPushEnabled())
                .build();
    }

    /** Maps a {@link NotificationLog} entity to its response DTO. */
    private NotificationLogResponse toLogResponse(NotificationLog log) {
        return NotificationLogResponse.builder()
                .id(log.getId())
                .notificationType(log.getNotificationType())
                .channel(log.getChannel())
                .status(log.getStatus())
                .message(log.getMessage())
                .errorMessage(log.getErrorMessage())
                .attemptCount(log.getAttemptCount())
                .sentAt(log.getSentAt())
                .referenceId(log.getReferenceId())
                .build();
    }
}
