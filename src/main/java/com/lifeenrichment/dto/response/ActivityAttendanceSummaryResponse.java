package com.lifeenrichment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Attendance summary for a single activity — counts by status and overall attendance rate.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityAttendanceSummaryResponse {

    private UUID activityId;
    private String activityTitle;

    /** Total number of residents who have an attendance log for this activity. */
    private long totalLogged;

    private long attended;
    private long absent;
    private long declined;

    /**
     * Attendance rate as a percentage (0–100), calculated as
     * {@code attended / totalLogged * 100}. Zero when no logs exist.
     */
    private double attendanceRate;
}
