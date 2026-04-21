package com.lifeenrichment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeenrichment.dto.request.LogAttendanceRequest;
import com.lifeenrichment.dto.response.ActivityAttendanceSummaryResponse;
import com.lifeenrichment.dto.response.AttendanceLogResponse;
import com.lifeenrichment.dto.response.ResidentParticipationResponse;
import com.lifeenrichment.entity.AttendanceLog.AssistanceLevel;
import com.lifeenrichment.entity.AttendanceLog.AttendanceStatus;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.security.config.SecurityConfig;
import com.lifeenrichment.security.jwt.JwtUtils;
import com.lifeenrichment.service.AttendanceService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceController.class)
@Import(SecurityConfig.class)
class AttendanceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AttendanceService attendanceService;
    @MockBean UserRepository userRepository;
    @MockBean JwtUtils jwtUtils;
    @MockBean UserDetailsService userDetailsService;

    private static final UUID ACTIVITY_ID  = UUID.randomUUID();
    private static final UUID RESIDENT_ID  = UUID.randomUUID();
    private static final UUID USER_ID      = UUID.randomUUID();

    private static final LocalDateTime LOGGED_AT = LocalDateTime.of(2026, 5, 1, 14, 0);

    private static final AttendanceLogResponse SAMPLE_LOG = AttendanceLogResponse.builder()
            .id(UUID.randomUUID())
            .activityId(ACTIVITY_ID)
            .activityTitle("Morning Yoga")
            .residentId(RESIDENT_ID)
            .residentName("Jane Doe")
            .status(AttendanceStatus.ATTENDED)
            .assistanceLevel(AssistanceLevel.NONE)
            .loggedAt(LOGGED_AT)
            .loggedByName("staff@facility.com")
            .build();

    private static final ActivityAttendanceSummaryResponse SAMPLE_SUMMARY =
            ActivityAttendanceSummaryResponse.builder()
                    .activityId(ACTIVITY_ID)
                    .activityTitle("Morning Yoga")
                    .totalLogged(5)
                    .attended(4)
                    .absent(1)
                    .declined(0)
                    .attendanceRate(80.0)
                    .build();

    private static final ResidentParticipationResponse LOW_PARTICIPANT =
            ResidentParticipationResponse.builder()
                    .residentId(RESIDENT_ID)
                    .residentName("John Smith")
                    .totalActivities(10)
                    .attended(3)
                    .participationRate(30.0)
                    .flaggedAsLow(true)
                    .build();

    private User staffUser() {
        return User.builder()
                .id(USER_ID)
                .email("staff@facility.com")
                .role(User.Role.STAFF)
                .build();
    }

    private User directorUser() {
        return User.builder()
                .id(USER_ID)
                .email("director@facility.com")
                .role(User.Role.DIRECTOR)
                .build();
    }

    // ── POST /attendance ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "STAFF", username = "staff@facility.com")
    void logAttendance_returns200_onValidRequest() throws Exception {
        when(userRepository.findByEmail("staff@facility.com"))
                .thenReturn(Optional.of(staffUser()));
        when(attendanceService.logAttendance(any(), any())).thenReturn(SAMPLE_LOG);

        LogAttendanceRequest body = LogAttendanceRequest.builder()
                .activityId(ACTIVITY_ID)
                .residentId(RESIDENT_ID)
                .status(AttendanceStatus.ATTENDED)
                .build();

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATTENDED"))
                .andExpect(jsonPath("$.residentName").value("Jane Doe"));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@facility.com")
    void logAttendance_returns200_forDirectorRole() throws Exception {
        when(userRepository.findByEmail("director@facility.com"))
                .thenReturn(Optional.of(directorUser()));
        when(attendanceService.logAttendance(any(), any())).thenReturn(SAMPLE_LOG);

        LogAttendanceRequest body = LogAttendanceRequest.builder()
                .activityId(ACTIVITY_ID)
                .residentId(RESIDENT_ID)
                .status(AttendanceStatus.ATTENDED)
                .build();

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF", username = "staff@facility.com")
    void logAttendance_returns400_onValidationError() throws Exception {
        // activityId is missing — should fail @NotNull validation
        LogAttendanceRequest bad = LogAttendanceRequest.builder()
                .residentId(RESIDENT_ID)
                .status(AttendanceStatus.ATTENDED)
                .build();

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STAFF", username = "staff@facility.com")
    void logAttendance_returns400_whenResidentNotEnrolled() throws Exception {
        when(userRepository.findByEmail("staff@facility.com"))
                .thenReturn(Optional.of(staffUser()));
        when(attendanceService.logAttendance(any(), any()))
                .thenThrow(new BusinessException("Resident is not enrolled. Set walkOn=true."));

        LogAttendanceRequest body = LogAttendanceRequest.builder()
                .activityId(ACTIVITY_ID)
                .residentId(RESIDENT_ID)
                .status(AttendanceStatus.ATTENDED)
                .build();

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STAFF", username = "staff@facility.com")
    void logAttendance_returns404_whenActivityNotFound() throws Exception {
        when(userRepository.findByEmail("staff@facility.com"))
                .thenReturn(Optional.of(staffUser()));
        when(attendanceService.logAttendance(any(), any()))
                .thenThrow(new ResourceNotFoundException("Activity", ACTIVITY_ID));

        LogAttendanceRequest body = LogAttendanceRequest.builder()
                .activityId(ACTIVITY_ID)
                .residentId(RESIDENT_ID)
                .status(AttendanceStatus.ATTENDED)
                .build();

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void logAttendance_returns403_whenUnauthenticated() throws Exception {
        LogAttendanceRequest body = LogAttendanceRequest.builder()
                .activityId(ACTIVITY_ID)
                .residentId(RESIDENT_ID)
                .status(AttendanceStatus.ATTENDED)
                .build();

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── GET /attendance/resident/{residentId} ─────────────────────────────────

    @Test
    @WithMockUser(roles = "STAFF")
    void getResidentHistory_returns200_withFullHistory() throws Exception {
        when(attendanceService.getResidentHistory(eq(RESIDENT_ID), isNull(), isNull()))
                .thenReturn(List.of(SAMPLE_LOG));

        mockMvc.perform(get("/api/v1/attendance/resident/{residentId}", RESIDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].residentName").value("Jane Doe"))
                .andExpect(jsonPath("$[0].activityTitle").value("Morning Yoga"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getResidentHistory_returns200_withDateFilter() throws Exception {
        when(attendanceService.getResidentHistory(eq(RESIDENT_ID), any(), any()))
                .thenReturn(List.of(SAMPLE_LOG));

        mockMvc.perform(get("/api/v1/attendance/resident/{residentId}", RESIDENT_ID)
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ATTENDED"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getResidentHistory_returns404_whenResidentNotFound() throws Exception {
        when(attendanceService.getResidentHistory(eq(RESIDENT_ID), isNull(), isNull()))
                .thenThrow(new ResourceNotFoundException("Resident", RESIDENT_ID));

        mockMvc.perform(get("/api/v1/attendance/resident/{residentId}", RESIDENT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getResidentHistory_returns403_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/resident/{residentId}", RESIDENT_ID))
                .andExpect(status().isForbidden());
    }

    // ── GET /attendance/activity/{activityId}/summary ─────────────────────────

    @Test
    @WithMockUser(roles = "STAFF")
    void getActivitySummary_returns200_withSummary() throws Exception {
        when(attendanceService.getActivitySummary(ACTIVITY_ID)).thenReturn(SAMPLE_SUMMARY);

        mockMvc.perform(get("/api/v1/attendance/activity/{activityId}/summary", ACTIVITY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityTitle").value("Morning Yoga"))
                .andExpect(jsonPath("$.attended").value(4))
                .andExpect(jsonPath("$.attendanceRate").value(80.0));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void getActivitySummary_returns200_forDirectorRole() throws Exception {
        when(attendanceService.getActivitySummary(ACTIVITY_ID)).thenReturn(SAMPLE_SUMMARY);

        mockMvc.perform(get("/api/v1/attendance/activity/{activityId}/summary", ACTIVITY_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getActivitySummary_returns404_whenActivityNotFound() throws Exception {
        when(attendanceService.getActivitySummary(ACTIVITY_ID))
                .thenThrow(new ResourceNotFoundException("Activity", ACTIVITY_ID));

        mockMvc.perform(get("/api/v1/attendance/activity/{activityId}/summary", ACTIVITY_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getActivitySummary_returns403_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/activity/{activityId}/summary", ACTIVITY_ID))
                .andExpect(status().isForbidden());
    }

    // ── GET /attendance/participation/low ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void getLowParticipation_returns200_withDefaults() throws Exception {
        when(attendanceService.getLowParticipationResidents(0, 0))
                .thenReturn(List.of(LOW_PARTICIPANT));

        mockMvc.perform(get("/api/v1/attendance/participation/low"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].residentName").value("John Smith"))
                .andExpect(jsonPath("$[0].participationRate").value(30.0))
                .andExpect(jsonPath("$[0].flaggedAsLow").value(true));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void getLowParticipation_returns200_withCustomParams() throws Exception {
        when(attendanceService.getLowParticipationResidents(70, 60))
                .thenReturn(List.of(LOW_PARTICIPANT));

        mockMvc.perform(get("/api/v1/attendance/participation/low")
                        .param("threshold", "70")
                        .param("lookbackDays", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flaggedAsLow").value(true));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getLowParticipation_returns403_forStaffRole() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/participation/low"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLowParticipation_returns403_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/participation/low"))
                .andExpect(status().isForbidden());
    }
}
