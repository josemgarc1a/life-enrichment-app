package com.lifeenrichment.service;

import com.lifeenrichment.dto.response.AuditLogResponse;
import com.lifeenrichment.entity.AuditLog;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // ── Action constants ──────────────────────────────────────────────────────

    public static final String LOGIN_SUCCESS              = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED               = "LOGIN_FAILED";
    public static final String LOGOUT                     = "LOGOUT";
    public static final String TOKEN_REFRESHED            = "TOKEN_REFRESHED";
    public static final String PASSWORD_RESET_REQUESTED   = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED   = "PASSWORD_RESET_COMPLETED";
    public static final String REGISTER                   = "REGISTER";

    // ── Logging ───────────────────────────────────────────────────────────────

    /**
     * Persists an audit event. Runs in its own transaction so a rollback in
     * the calling service does not suppress the log entry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String details) {
        AuditLog entry = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType("AUTH")
                .ipAddress(resolveClientIp())
                .details(details)
                .build();
        auditLogRepository.save(entry);
        log.debug("Audit: action={} user={}", action, user != null ? user.getEmail() : "anonymous");
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> findLogs(UUID userId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        LocalDateTime resolvedFrom = from != null ? from : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime resolvedTo   = to   != null ? to   : LocalDateTime.now().plusDays(1);
        return auditLogRepository
                .findWithFilters(userId, resolvedFrom, resolvedTo, pageable)
                .map(AuditLogResponse::from);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            return (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[0].trim()
                    : request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
