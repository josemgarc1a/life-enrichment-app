package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "resident_family_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"resident_id", "user_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResidentFamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "relationship_label")
    private String relationshipLabel;

    @CreationTimestamp
    @Column(name = "linked_at", updatable = false)
    private LocalDateTime linkedAt;
}
