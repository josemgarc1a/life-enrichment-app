package com.lifeenrichment.dto.request;

import com.lifeenrichment.entity.Resident;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Request body for the {@code PUT /api/v1/residents/{id}} endpoint.
 *
 * <p>Supports partial updates — every field is optional. {@code null} means "do not change";
 * only non-null values are applied by {@link com.lifeenrichment.service.ResidentService#updateResident}.
 */
@Getter @Setter @Builder
public class UpdateResidentRequest {

    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String roomNumber;
    private Resident.CareLevel careLevel;
    private String preferences;
}
