package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.Activity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full activity response returned by create, get, update, and cancel endpoints.
 * Includes current enrollment count and the list of enrolled resident IDs.
 */
@Getter @Builder
public class ActivityResponse {

    private UUID id;
    private String title;
    private String description;
    private Activity.Category category;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private String recurrenceRule;
    private Activity.Status status;
    private UUID createdById;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Current number of residents enrolled in this activity. */
    private long enrollmentCount;

    /** UUIDs of all residents currently enrolled in this activity. */
    private List<UUID> enrolledResidentIds;
}
