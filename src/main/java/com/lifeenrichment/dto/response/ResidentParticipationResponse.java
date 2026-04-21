package com.lifeenrichment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Participation summary for a resident over a rolling lookback window.
 * Used by the low-participation detection endpoint.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResidentParticipationResponse {

    private UUID residentId;
    private String residentName;

    /** Total activities logged for this resident in the lookback window. */
    private long totalActivities;

    /** Number of those activities where status was ATTENDED. */
    private long attended;

    /**
     * Participation rate as a percentage (0–100).
     * Zero when {@code totalActivities} is zero.
     */
    private double participationRate;

    /** True when {@code participationRate} is below the requested threshold. */
    private boolean flaggedAsLow;
}
