package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Join entity recording a resident's enrollment in a scheduled activity.
 *
 * <p>The unique constraint on {@code (activity_id, resident_id)} prevents a resident
 * from being enrolled in the same activity more than once.
 */
@Entity
@Table(
        name = "activity_enrollments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "resident_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrolled_by")
    private User enrolledBy;

    @CreationTimestamp
    @Column(name = "enrolled_at", updatable = false)
    private LocalDateTime enrolledAt;
}
