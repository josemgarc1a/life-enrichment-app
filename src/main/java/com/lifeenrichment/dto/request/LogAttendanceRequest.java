package com.lifeenrichment.dto.request;

import com.lifeenrichment.entity.AttendanceLog.AssistanceLevel;
import com.lifeenrichment.entity.AttendanceLog.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/attendance}.
 * Logs or updates attendance for a resident at a specific activity.
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LogAttendanceRequest {

    @NotNull
    private UUID activityId;

    @NotNull
    private UUID residentId;

    @NotNull
    private AttendanceStatus status;

    /** Defaults to {@code NONE} when not provided. */
    @Builder.Default
    private AssistanceLevel assistanceLevel = AssistanceLevel.NONE;

    private String assistanceNotes;

    /**
     * Set to {@code true} when a resident attends without being pre-enrolled
     * (e.g. wandered in, or matched via AI photo recognition).
     * When true, the service skips the enrollment check and automatically
     * creates an {@code ActivityEnrollment} record before logging attendance.
     * Defaults to {@code false}.
     */
    @Builder.Default
    private boolean walkOn = false;
}
