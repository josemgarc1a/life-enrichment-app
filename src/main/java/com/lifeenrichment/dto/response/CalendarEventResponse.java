package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.Activity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight activity representation for the calendar view endpoint.
 * Follows the FullCalendar event object conventions ({@code start}, {@code end}).
 */
@Getter @Builder
public class CalendarEventResponse {

    private UUID id;
    private String title;

    /** ISO-8601 start datetime, compatible with FullCalendar {@code start} field. */
    private LocalDateTime start;

    /** ISO-8601 end datetime, compatible with FullCalendar {@code end} field. */
    private LocalDateTime end;

    private Activity.Category category;
    private Activity.Status status;
    private String location;
    private Integer capacity;
    private long enrollmentCount;
}
