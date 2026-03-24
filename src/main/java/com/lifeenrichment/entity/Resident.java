package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a resident living at the assisted-living facility.
 *
 * <p>Residents are never hard-deleted; the {@link #isActive} flag is set to {@code false}
 * to archive a discharged resident while preserving their historical record and family
 * member links. Only active residents appear in search results and activity scheduling.
 */
@Entity
@Table(name = "residents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Resident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "room_number")
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_level")
    private CareLevel careLevel;

    @Column(columnDefinition = "TEXT")
    private String preferences;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Indicates the level of assistance a resident requires for daily activities.
     *
     * <ul>
     *   <li>{@code LOW} — largely independent, minimal staff assistance needed</li>
     *   <li>{@code MEDIUM} — requires some daily assistance</li>
     *   <li>{@code HIGH} — requires continuous or high-frequency staff support</li>
     * </ul>
     */
    public enum CareLevel {
        LOW, MEDIUM, HIGH
    }
}
