package com.lifeenrichment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing an application user.
 *
 * <p>Users can be Directors (full admin access), Staff (day-to-day operations),
 * or Family Members (read-only access to their linked resident's information).
 * Passwords are stored as BCrypt hashes; plain-text passwords are never persisted.
 */
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Access roles assigned to a user at registration time.
     *
     * <ul>
     *   <li>{@code DIRECTOR} — full administrative access including reports and user management</li>
     *   <li>{@code STAFF} — day-to-day operations; can view residents and log activities</li>
     *   <li>{@code FAMILY_MEMBER} — limited read-only access linked to specific residents</li>
     * </ul>
     */
    public enum Role {
        DIRECTOR, STAFF, FAMILY_MEMBER
    }
}
