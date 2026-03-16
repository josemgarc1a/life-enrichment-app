package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs",
       indexes = {
           @Index(name = "idx_audit_user", columnList = "user_id"),
           @Index(name = "idx_audit_occurred_at", columnList = "occurred_at")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String action;          // e.g. "LOGIN", "LOGOUT", "RESIDENT_UPDATE"

    @Column(name = "entity_type")
    private String entityType;      // e.g. "RESIDENT", "ACTIVITY"

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "occurred_at", updatable = false)
    private LocalDateTime occurredAt;
}
