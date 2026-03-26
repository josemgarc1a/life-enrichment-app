package com.lifeenrichment.dto.request;

import com.lifeenrichment.entity.Activity;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Request body for {@code PUT /api/v1/activities/{id}}.
 * All fields are optional; only non-null values are applied to the existing activity.
 */
@Getter @Setter @Builder
public class UpdateActivityRequest {

    private String title;

    private String description;

    private Activity.Category category;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Min(1)
    private Integer capacity;

    private String recurrenceRule;
}
