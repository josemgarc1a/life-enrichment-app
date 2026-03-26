package com.lifeenrichment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeenrichment.dto.request.CreateActivityRequest;
import com.lifeenrichment.dto.request.EnrollResidentRequest;
import com.lifeenrichment.dto.request.UpdateActivityRequest;
import com.lifeenrichment.dto.response.ActivityResponse;
import com.lifeenrichment.dto.response.CalendarEventResponse;
import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.security.config.SecurityConfig;
import com.lifeenrichment.security.jwt.JwtUtils;
import com.lifeenrichment.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityController.class)
@Import(SecurityConfig.class)
class ActivityControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ActivityService activityService;
    @MockBean UserRepository userRepository;
    @MockBean JwtUtils jwtUtils;
    @MockBean UserDetailsService userDetailsService;

    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID RESIDENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static final LocalDateTime START = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 5, 1, 11, 0);

    private static final ActivityResponse SAMPLE_RESPONSE = ActivityResponse.builder()
            .id(ACTIVITY_ID)
            .title("Morning Yoga")
            .category(Activity.Category.FITNESS)
            .location("Garden Room")
            .startTime(START)
            .endTime(END)
            .capacity(10)
            .status(Activity.Status.SCHEDULED)
            .enrollmentCount(0)
            .enrolledResidentIds(List.of())
            .build();

    private static final CalendarEventResponse SAMPLE_CALENDAR = CalendarEventResponse.builder()
            .id(ACTIVITY_ID)
            .title("Morning Yoga")
            .start(START)
            .end(END)
            .category(Activity.Category.FITNESS)
            .status(Activity.Status.SCHEDULED)
            .location("Garden Room")
            .capacity(10)
            .enrollmentCount(0)
            .build();

    private User directorUser() {
        return User.builder()
                .id(USER_ID)
                .email("director@facility.com")
                .role(User.Role.DIRECTOR)
                .build();
    }

    // ── POST /activities ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@facility.com")
    void createActivity_returns201_onValidRequest() throws Exception {
        when(userRepository.findByEmail("director@facility.com"))
                .thenReturn(Optional.of(directorUser()));
        when(activityService.createActivity(any(), any())).thenReturn(SAMPLE_RESPONSE);

        CreateActivityRequest body = CreateActivityRequest.builder()
                .title("Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(START)
                .endTime(END)
                .capacity(10)
                .build();

        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Morning Yoga"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@facility.com")
    void createActivity_returns400_onValidationError() throws Exception {
        CreateActivityRequest badBody = CreateActivityRequest.builder()
                // title is blank — validation should fail
                .title("")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(START)
                .endTime(END)
                .capacity(10)
                .build();

        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badBody)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void createActivity_returns403_forStaffRole() throws Exception {
        CreateActivityRequest body = CreateActivityRequest.builder()
                .title("Yoga")
                .category(Activity.Category.FITNESS)
                .location("Room")
                .startTime(START)
                .endTime(END)
                .capacity(5)
                .build();

        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── GET /activities/{id} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "STAFF")
    void getActivity_returns200_whenFound() throws Exception {
        when(activityService.getActivity(ACTIVITY_ID)).thenReturn(SAMPLE_RESPONSE);

        mockMvc.perform(get("/api/v1/activities/{id}", ACTIVITY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACTIVITY_ID.toString()))
                .andExpect(jsonPath("$.title").value("Morning Yoga"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getActivity_returns404_whenNotFound() throws Exception {
        when(activityService.getActivity(ACTIVITY_ID))
                .thenThrow(new ResourceNotFoundException("Activity", ACTIVITY_ID));

        mockMvc.perform(get("/api/v1/activities/{id}", ACTIVITY_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getActivity_returns403_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/activities/{id}", ACTIVITY_ID))
                .andExpect(status().isForbidden());
    }

    // ── GET /activities ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "STAFF")
    void listActivities_returns200_withEmptyResult() throws Exception {
        when(activityService.listActivities(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void listActivities_passesFilterParams_toService() throws Exception {
        Page<ActivityResponse> page = new PageImpl<>(List.of(SAMPLE_RESPONSE));
        when(activityService.listActivities(eq(Activity.Category.FITNESS), eq(Activity.Status.SCHEDULED), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/activities")
                        .param("category", "FITNESS")
                        .param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── GET /activities/calendar ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "STAFF")
    void getCalendarEvents_returns200_withEvents() throws Exception {
        when(activityService.getCalendarEvents(any(), any()))
                .thenReturn(List.of(SAMPLE_CALENDAR));

        mockMvc.perform(get("/api/v1/activities/calendar")
                        .param("startDate", "2026-05-01T00:00:00")
                        .param("endDate", "2026-05-07T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Morning Yoga"))
                .andExpect(jsonPath("$[0].category").value("FITNESS"));
    }

    // ── PUT /activities/{id} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void updateActivity_returns200_onSuccess() throws Exception {
        when(activityService.updateActivity(eq(ACTIVITY_ID), any())).thenReturn(SAMPLE_RESPONSE);

        UpdateActivityRequest body = UpdateActivityRequest.builder()
                .title("Evening Yoga")
                .build();

        mockMvc.perform(put("/api/v1/activities/{id}", ACTIVITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void updateActivity_returns403_forStaffRole() throws Exception {
        mockMvc.perform(put("/api/v1/activities/{id}", ACTIVITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── POST /activities/{id}/cancel ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void cancelActivity_returns200_onSuccess() throws Exception {
        ActivityResponse cancelled = ActivityResponse.builder()
                .id(ACTIVITY_ID).title("Morning Yoga")
                .status(Activity.Status.CANCELLED)
                .category(Activity.Category.FITNESS)
                .location("Garden Room").capacity(10)
                .enrollmentCount(0).enrolledResidentIds(List.of())
                .build();

        when(activityService.cancelActivity(ACTIVITY_ID)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/activities/{id}/cancel", ACTIVITY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void cancelActivity_returns404_whenNotFound() throws Exception {
        when(activityService.cancelActivity(ACTIVITY_ID))
                .thenThrow(new ResourceNotFoundException("Activity", ACTIVITY_ID));

        mockMvc.perform(post("/api/v1/activities/{id}/cancel", ACTIVITY_ID))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /activities/{id} ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void deleteActivity_returns204_onSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/activities/{id}", ACTIVITY_ID))
                .andExpect(status().isNoContent());

        verify(activityService).deleteActivity(ACTIVITY_ID);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void deleteActivity_returns403_forStaffRole() throws Exception {
        mockMvc.perform(delete("/api/v1/activities/{id}", ACTIVITY_ID))
                .andExpect(status().isForbidden());
    }

    // ── POST /activities/{id}/enrollments ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@facility.com")
    void enrollResident_returns200_onSuccess() throws Exception {
        when(userRepository.findByEmail("director@facility.com"))
                .thenReturn(Optional.of(directorUser()));
        when(activityService.enrollResident(any(), any(), any())).thenReturn(SAMPLE_RESPONSE);

        EnrollResidentRequest body = EnrollResidentRequest.builder()
                .residentId(RESIDENT_ID).build();

        mockMvc.perform(post("/api/v1/activities/{id}/enrollments", ACTIVITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR", username = "director@facility.com")
    void enrollResident_returns400_whenCapacityExceeded() throws Exception {
        when(userRepository.findByEmail("director@facility.com"))
                .thenReturn(Optional.of(directorUser()));
        when(activityService.enrollResident(any(), any(), any()))
                .thenThrow(new BusinessException("Activity is at full capacity"));

        EnrollResidentRequest body = EnrollResidentRequest.builder()
                .residentId(RESIDENT_ID).build();

        mockMvc.perform(post("/api/v1/activities/{id}/enrollments", ACTIVITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void enrollResident_returns403_forStaffRole() throws Exception {
        EnrollResidentRequest body = EnrollResidentRequest.builder()
                .residentId(RESIDENT_ID).build();

        mockMvc.perform(post("/api/v1/activities/{id}/enrollments", ACTIVITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /activities/{id}/enrollments/{residentId} ──────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void unenrollResident_returns200_onSuccess() throws Exception {
        when(activityService.unenrollResident(ACTIVITY_ID, RESIDENT_ID)).thenReturn(SAMPLE_RESPONSE);

        mockMvc.perform(delete("/api/v1/activities/{id}/enrollments/{residentId}",
                        ACTIVITY_ID, RESIDENT_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void unenrollResident_returns404_whenEnrollmentMissing() throws Exception {
        when(activityService.unenrollResident(ACTIVITY_ID, RESIDENT_ID))
                .thenThrow(new ResourceNotFoundException("ActivityEnrollment", RESIDENT_ID));

        mockMvc.perform(delete("/api/v1/activities/{id}/enrollments/{residentId}",
                        ACTIVITY_ID, RESIDENT_ID))
                .andExpect(status().isNotFound());
    }
}
