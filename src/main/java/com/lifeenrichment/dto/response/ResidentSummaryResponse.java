package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.Resident;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter @Builder
public class ResidentSummaryResponse {

    private UUID id;
    private String fullName;
    private String roomNumber;
    private Resident.CareLevel careLevel;
    private String photoUrl;
    private boolean isActive;
}
