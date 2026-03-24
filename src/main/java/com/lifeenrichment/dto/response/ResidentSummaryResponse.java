package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.Resident;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Lightweight resident summary used in search/list responses.
 * Contains only the fields needed for a list-row display; use
 * {@link ResidentResponse} to retrieve the full profile.
 */
@Getter @Builder
public class ResidentSummaryResponse {

    private UUID id;
    private String fullName;
    private String roomNumber;
    private Resident.CareLevel careLevel;
    private String photoUrl;
    private boolean isActive;
}
