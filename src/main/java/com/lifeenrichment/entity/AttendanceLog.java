package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_logs",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_attendance_activity_resident",
               columnNames = {"activity_id", "resident_id"}
       ),
       indexes = {
           @Index(name = "idx_attendance_resident", columnList = "resident_id"),
           @Index(name = "idx_attendance_activity", columnList = "activity_id"),
           @Index(name = "idx_attendance_logged_at", columnList = "logged_at")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistance_level")
    @Builder.Default
    private AssistanceLevel assistanceLevel = AssistanceLevel.NONE;

    @Column(name = "assistance_notes", columnDefinition = "TEXT")
    private String assistanceNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logged_by", nullable = false)
    private User loggedBy;

    @CreationTimestamp
    @Column(name = "logged_at", updatable = false)
    private LocalDateTime loggedAt;

    public enum AttendanceStatus {
        ATTENDED, ABSENT, DECLINED
    }

    public enum AssistanceLevel {
        NONE, MINIMAL, MODERATE, FULL
    }
}
