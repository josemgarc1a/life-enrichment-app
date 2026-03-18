package com.lifeenrichment.controller;

import com.lifeenrichment.dto.response.AuditLogResponse;
import com.lifeenrichment.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "HIPAA-adjacent audit log — Director access only")
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "List audit logs",
               description = "Returns paginated audit events. Optionally filter by userId and/or date range. Director role required.")
    @ApiResponse(responseCode = "200", description = "Audit logs returned")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<Page<AuditLogResponse>> getLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(auditService.findLogs(userId, from, to, pageable));
    }
}
