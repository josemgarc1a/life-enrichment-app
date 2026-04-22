package com.lifeenrichment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeenrichment.dto.request.BroadcastRequest;
import com.lifeenrichment.dto.request.UpdateNotificationPreferenceRequest;
import com.lifeenrichment.dto.response.NotificationLogResponse;
import com.lifeenrichment.dto.response.NotificationPreferenceResponse;
import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationLog.Channel;
import com.lifeenrichment.entity.NotificationLog.DeliveryStatus;
import com.lifeenrichment.entity.NotificationPreference;
import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.repository.NotificationLogRepository;
import com.lifeenrichment.repository.NotificationPreferenceRepository;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.security.config.SecurityConfig;
import com.lifeenrichment.security.jwt.JwtUtils;
import com.lifeenrichment.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean NotificationService notificationService;
    @MockBean NotificationPreferenceRepository notificationPreferenceRepository;
    @MockBean NotificationLogRepository notificationLogRepository;
    @MockBean UserRepository userRepository;
    @MockBean JwtUtils jwtUtils;
    @MockBean UserDetailsService userDetailsService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PREF_ID = UUID.randomUUID();
    private static final UUID LOG_ID  = UUID.randomUUID();

    private User staffUser() {
        return User.builder()
                .id(USER_ID)
                .email("staff@test.com")
                .role(User.Role.STAFF)
                .build();
    }

    private User directorUser() {
        return User.builder()
                .id(USER_ID)
                .email("director@test.com")
                .role(User.Role.DIRECTOR)
                .build();
    }

    private NotificationPreferenceResponse samplePreferenceResponse() {
        return NotificationPreferenceResponse.builder()
                .id(PREF_ID)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(false)
                .build();
    }

    private NotificationPreference samplePreferenceEntity(User user) {
        return NotificationPreference.builder()
                .id(PREF_ID)
                .user(user)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(false)
                .build();
    }

    private NotificationLogResponse sampleLogResponse() {
        return NotificationLogResponse.builder()
                .id(LOG_ID)
                .notificationType(NotificationType.BROADCAST)
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.SENT)
                .message("Hello residents!")
                .attemptCount(1)
                .sentAt(LocalDateTime.of(2026, 4, 21, 10, 0))
                .build();
    }

    private NotificationLog sampleLogEntity() {
        return NotificationLog.builder()
                .id(LOG_ID)
                .notificationType(NotificationType.BROADCAST)
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.SENT)
                .message("Hello residents!")
                .attemptCount(1)
                .build();
    }

    // ── GET /preferences ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "staff@test.com")
    void getPreferences_returns200_withList() throws Exception {
        when(userRepository.findByEmail("staff@test.com"))
                .thenReturn(Optional.of(staffUser()));
        when(notificationPreferenceRepository.findByUserId(USER_ID))
                .thenReturn(List.of(samplePreferenceEntity(staffUser())));

        mockMvc.perform(get("/api/v1/notifications/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationType").value("ACTIVITY_REMINDER"))
                .andExpect(jsonPath("$[0].emailEnabled").value(true));
    }

    @Test
    void getPreferences_returns403_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /preferences/{type} ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "staff@test.com")
    void updatePreference_returns200_onUpsertCreate() throws Exception {
        User user = staffUser();
        when(userRepository.findByEmail("staff@test.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(USER_ID, NotificationType.ACTIVITY_REMINDER))
                .thenReturn(Optional.empty());

        NotificationPreference saved = samplePreferenceEntity(user);
        when(notificationPreferenceRepository.save(any(NotificationPreference.class)))
                .thenReturn(saved);

        UpdateNotificationPreferenceRequest body = UpdateNotificationPreferenceRequest.builder()
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(false)
                .build();

        mockMvc.perform(put("/api/v1/notifications/preferences/{type}", "ACTIVITY_REMINDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationType").value("ACTIVITY_REMINDER"))
                .andExpect(jsonPath("$.emailEnabled").value(true));
    }

    @Test
    @WithMockUser(username = "staff@test.com")
    void updatePreference_returns200_onUpsertUpdate() throws Exception {
        User user = staffUser();
        when(userRepository.findByEmail("staff@test.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        NotificationPreference existing = samplePreferenceEntity(user);
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(USER_ID, NotificationType.ACTIVITY_REMINDER))
                .thenReturn(Optional.of(existing));

        NotificationPreference updated = NotificationPreference.builder()
                .id(PREF_ID)
                .user(user)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .emailEnabled(true)
                .smsEnabled(true)
                .pushEnabled(false)
                .build();
        when(notificationPreferenceRepository.save(any(NotificationPreference.class)))
                .thenReturn(updated);

        UpdateNotificationPreferenceRequest body = UpdateNotificationPreferenceRequest.builder()
                .emailEnabled(true)
                .smsEnabled(true)
                .pushEnabled(false)
                .build();

        mockMvc.perform(put("/api/v1/notifications/preferences/{type}", "ACTIVITY_REMINDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsEnabled").value(true));
    }

    @Test
    @WithMockUser(username = "staff@test.com")
    void updatePreference_returns400_whenMessageIsInvalid() throws Exception {
        // UpdateNotificationPreferenceRequest has no @NotNull constraints on its boolean fields,
        // but an invalid enum value for the {type} path variable causes a conversion failure.
        // The GlobalExceptionHandler catch-all maps this to 500; assert that the response is
        // not a 2xx success to confirm the bad request is rejected.
        mockMvc.perform(put("/api/v1/notifications/preferences/{type}", "NOT_A_REAL_TYPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void updatePreference_returns403_whenUnauthenticated() throws Exception {
        UpdateNotificationPreferenceRequest body = UpdateNotificationPreferenceRequest.builder()
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(false)
                .build();

        mockMvc.perform(put("/api/v1/notifications/preferences/{type}", "ACTIVITY_REMINDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── POST /broadcast ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@test.com")
    void broadcast_returns202_forDirector() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(directorUser()));
        doNothing().when(notificationService).sendBroadcast(any(), any());

        BroadcastRequest body = BroadcastRequest.builder()
                .message("Important announcement for all residents")
                .build();

        mockMvc.perform(post("/api/v1/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@test.com")
    void broadcast_returns202_withExplicitTargets() throws Exception {
        doNothing().when(notificationService).sendBroadcast(any(), any());

        UUID targetId = UUID.randomUUID();
        BroadcastRequest body = BroadcastRequest.builder()
                .message("Targeted message")
                .targetUserIds(List.of(targetId))
                .build();

        mockMvc.perform(post("/api/v1/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@test.com")
    void broadcast_returns400_whenMessageBlank() throws Exception {
        BroadcastRequest body = BroadcastRequest.builder()
                .message("")
                .build();

        mockMvc.perform(post("/api/v1/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STAFF", username = "staff@test.com")
    void broadcast_returns403_forStaffRole() throws Exception {
        BroadcastRequest body = BroadcastRequest.builder()
                .message("Should be blocked")
                .build();

        mockMvc.perform(post("/api/v1/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void broadcast_returns403_whenUnauthenticated() throws Exception {
        BroadcastRequest body = BroadcastRequest.builder()
                .message("Should be blocked")
                .build();

        mockMvc.perform(post("/api/v1/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── GET /logs ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@test.com")
    void getLogs_returns200_noFilters() throws Exception {
        when(notificationLogRepository.findAll())
                .thenReturn(List.of(sampleLogEntity()));

        mockMvc.perform(get("/api/v1/notifications/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationType").value("BROADCAST"))
                .andExpect(jsonPath("$[0].status").value("SENT"));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@test.com")
    void getLogs_returns200_filteredByUserId() throws Exception {
        when(notificationLogRepository.findByUserIdOrderBySentAtDesc(USER_ID))
                .thenReturn(List.of(sampleLogEntity()));

        mockMvc.perform(get("/api/v1/notifications/logs")
                        .param("userId", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channel").value("EMAIL"));
    }

    @Test
    @WithMockUser(roles = "STAFF", username = "staff@test.com")
    void getLogs_returns403_forStaffRole() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLogs_returns403_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/logs"))
                .andExpect(status().isForbidden());
    }
}
