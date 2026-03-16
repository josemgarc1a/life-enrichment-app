package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

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
    private Category category;

    private String location;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    private Integer capacity;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;  // iCal RRULE format e.g. "FREQ=WEEKLY;BYDAY=MO,WE,FR"

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Category {
        FITNESS, ARTS, SOCIAL, COGNITIVE, MUSIC, OUTDOOR, OTHER
    }

    public enum Status {
        SCHEDULED, CANCELLED, COMPLETED
    }
}
