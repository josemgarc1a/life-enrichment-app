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

    /** Optional iCal RRULE string for recurring activities, e.g. {@code FREQ=WEEKLY;BYDAY=MO}. */
    private String recurrenceRule;
}
