package com.lifeenrichment.dto.request;

import com.lifeenrichment.entity.Activity;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Request body for {@code POST /api/v1/activities}.
 * All fields except {@code description} and {@code recurrenceRule} are required.
 */
@Getter @Setter @Builder
public class CreateActivityRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private Activity.Category category;

    @NotBlank
    private String location;

    @NotNull
    @Future
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    @NotNull
    @Min(1)
    private Integer capacity;

    /**
     * Set to {@code true} to create a recurring activity series.
     * When true, {@code dayOfWeek} is required and the service will generate
     * 8 weeks of occurrence rows automatically.
     */
    private boolean recurring;

    /**
     * Day of week for recurring activities, e.g. {@code "THURSDAY"}.
     * Required when {@code recurring = true}; ignored otherwise.
     * Accepted values: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.
     */
    private String dayOfWeek;
}
