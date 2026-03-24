package com.lifeenrichment.dto.request;

import com.lifeenrichment.entity.Resident;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter @Builder
public class UpdateResidentRequest {

    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String roomNumber;
    private Resident.CareLevel careLevel;
    private String preferences;
}
