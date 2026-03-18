package com.lifeenrichment.dto.response;

import com.lifeenrichment.entity.AuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

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
