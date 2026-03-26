package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a scheduled activity at the assisted-living facility.
 *
 * <p>Activities are soft-deleted by setting {@link #deletedAt}; they never disappear
 * from the database so that historical attendance records remain intact.
 * The {@link #recurrenceRule} field stores an iCal RRULE string (e.g.
 * {@code FREQ=WEEKLY;BYDAY=MO,WE,FR}) for recurring activities.
 */
@Entity
@Table(name = "activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private String location;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Integer capacity;

    /** iCal RRULE string, e.g. {@code FREQ=WEEKLY;BYDAY=MO,WE,FR}. Null for one-time activities. */
    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Soft-delete timestamp. Non-null means the activity has been deleted. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Activity categories used for filtering and reporting.
     */
    public enum Category {
        FITNESS, ARTS, SOCIAL, COGNITIVE, MUSIC, OUTDOOR, OTHER
    }

    /**
     * Lifecycle status of an activity.
     */
    public enum Status {
        SCHEDULED, CANCELLED, COMPLETED
    }
}
