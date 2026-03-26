package com.lifeenrichment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/activities/{id}/enrollments}.
 */
@Getter @Setter @Builder
public class EnrollResidentRequest {

    @NotNull
    private UUID residentId;
}
