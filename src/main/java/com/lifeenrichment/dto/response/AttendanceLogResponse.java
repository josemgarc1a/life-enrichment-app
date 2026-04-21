package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.AttendanceLog.AssistanceLevel;
import com.lifeenrichment.entity.AttendanceLog.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response returned when creating or retrieving an attendance log entry.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class AttendanceLogResponse {

    private UUID id;
    private UUID activityId;
    private String activityTitle;
    private UUID residentId;
    private String residentName;
    private AttendanceStatus status;
    private AssistanceLevel assistanceLevel;
    private String assistanceNotes;
    private LocalDateTime loggedAt;
    private String loggedByName;
}
