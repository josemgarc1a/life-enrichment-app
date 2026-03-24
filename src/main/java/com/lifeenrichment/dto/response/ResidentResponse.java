package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.Resident;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full resident profile response, including personal details, care level,
 * photo URL, and the list of linked family members.
 * Returned by create, get, and update endpoints.
 */
@Getter @Builder
public class ResidentResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String roomNumber;
    private Resident.CareLevel careLevel;
    private String preferences;
    private String photoUrl;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<FamilyMemberEntry> familyMembers;

    /** Represents a single family member linked to this resident. */
    @Getter @Builder
    public static class FamilyMemberEntry {
        private UUID userId;
        private String fullName;
        private String email;
        private String relationshipLabel;
    }
}
