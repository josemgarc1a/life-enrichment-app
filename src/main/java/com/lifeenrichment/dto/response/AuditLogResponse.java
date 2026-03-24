package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.AuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable response DTO representing a single audit log entry.
 * Constructed from an {@link com.lifeenrichment.entity.AuditLog} entity via {@link #from}.
 */
@Getter
@Builder
public class AuditLogResponse {

    private UUID id;
    private String action;
    private String entityType;
    private UUID entityId;
    private String ipAddress;
    private String details;
    private LocalDateTime occurredAt;
    private UUID userId;
    private String userEmail;

    /**
     * Maps an {@link com.lifeenrichment.entity.AuditLog} entity to this DTO.
     * Safely handles the case where {@code log.getUser()} is {@code null}
     * (e.g. events for unrecognized email addresses).
     */
    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .ipAddress(log.getIpAddress())
                .details(log.getDetails())
                .occurredAt(log.getOccurredAt())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .userEmail(log.getUser() != null ? log.getUser().getEmail() : null)
                .build();
    }
}
