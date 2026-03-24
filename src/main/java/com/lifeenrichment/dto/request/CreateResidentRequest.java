package com.lifeenrichment.dto.request;

import com.lifeenrichment.entity.Resident;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Request body for the {@code POST /api/v1/residents} endpoint.
 * All required fields are validated with Bean Validation constraints.
 */
@Getter @Setter @Builder
public class CreateResidentRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotNull
    private LocalDate dateOfBirth;

    @NotBlank
    private String roomNumber;

    @NotNull
    private Resident.CareLevel careLevel;

    private String preferences;
}
