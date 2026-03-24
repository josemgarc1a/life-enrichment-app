package com.lifeenrichment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeenrichment.dto.request.CreateResidentRequest;
import com.lifeenrichment.dto.request.LinkFamilyMemberRequest;
import com.lifeenrichment.dto.request.UpdateResidentRequest;
import com.lifeenrichment.dto.response.ResidentResponse;
import com.lifeenrichment.dto.response.ResidentSummaryResponse;
import com.lifeenrichment.entity.Resident;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.security.config.SecurityConfig;
import com.lifeenrichment.security.jwt.JwtUtils;
import com.lifeenrichment.service.ResidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResidentController.class)
@Import(SecurityConfig.class)
class ResidentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ResidentService residentService;
    @MockBean JwtUtils jwtUtils;
    @MockBean UserDetailsService userDetailsService;

    private static final UUID RESIDENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static final ResidentResponse SAMPLE_RESPONSE = ResidentResponse.builder()
            .id(RESIDENT_ID)
            .firstName("Alice")
            .lastName("Johnson")
            .dateOfBirth(LocalDate.of(1935, 4, 12))
            .roomNumber("101")
            .careLevel(Resident.CareLevel.LOW)
            .isActive(true)
            .familyMembers(List.of())
            .build();

    private static final ResidentSummaryResponse SAMPLE_SUMMARY = ResidentSummaryResponse.builder()
            .id(RESIDENT_ID)
            .fullName("Alice Johnson")
            .roomNumber("101")
            .careLevel(Resident.CareLevel.LOW)
            .isActive(true)
            .build();

    // ── POST /residents ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void createResident_returns201_onValidRequest() throws Exception {
        when(residentService.createResident(any())).thenReturn(SAMPLE_RESPONSE);

        CreateResidentRequest body = CreateResidentRequest.builder()
                .firstName("Alice").lastName("Johnson")
                .dateOfBirth(LocalDate.of(1935, 4, 12))
                .roomNumber("101").careLevel(Resident.CareLevel.LOW)
                .build();

        mockMvc.perform(post("/api/v1/residents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.roomNumber").value("101"));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void createResident_returns400_onMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/residents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void createResident_returns403_forStaffRole() throws Exception {
        CreateResidentRequest body = CreateResidentRequest.builder()
                .firstName("Alice").lastName("Johnson")
                .dateOfBirth(LocalDate.of(1935, 4, 12))
                .roomNumber("101").careLevel(Resident.CareLevel.LOW)
                .build();

        mockMvc.perform(post("/api/v1/residents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── GET /residents/{id} ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void getResident_returns200_whenFound() throws Exception {
        when(residentService.getResident(RESIDENT_ID)).thenReturn(SAMPLE_RESPONSE);

        mockMvc.perform(get("/api/v1/residents/{id}", RESIDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESIDENT_ID.toString()))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getResident_returns200_forStaffRole() throws Exception {
        when(residentService.getResident(RESIDENT_ID)).thenReturn(SAMPLE_RESPONSE);

        mockMvc.perform(get("/api/v1/residents/{id}", RESIDENT_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void getResident_returns404_whenNotFound() throws Exception {
        when(residentService.getResident(RESIDENT_ID))
                .thenThrow(new ResourceNotFoundException("Resident", RESIDENT_ID));

        mockMvc.perform(get("/api/v1/residents/{id}", RESIDENT_ID))
                .andExpect(status().isNotFound());
    }

    // ── PUT /residents/{id} ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void updateResident_returns200_onValidRequest() throws Exception {
        when(residentService.updateResident(eq(RESIDENT_ID), any())).thenReturn(SAMPLE_RESPONSE);

        UpdateResidentRequest body = UpdateResidentRequest.builder()
                .roomNumber("202").build();

        mockMvc.perform(put("/api/v1/residents/{id}", RESIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void updateResident_returns404_whenNotFound() throws Exception {
        when(residentService.updateResident(eq(RESIDENT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Resident", RESIDENT_ID));

        mockMvc.perform(put("/api/v1/residents/{id}", RESIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateResidentRequest.builder().build())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void updateResident_returns403_forStaffRole() throws Exception {
        mockMvc.perform(put("/api/v1/residents/{id}", RESIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateResidentRequest.builder().build())))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /residents/{id}/archive ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void archiveResident_returns204_onSuccess() throws Exception {
        doNothing().when(residentService).archiveResident(RESIDENT_ID);

        mockMvc.perform(delete("/api/v1/residents/{id}/archive", RESIDENT_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void archiveResident_returns404_whenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Resident", RESIDENT_ID))
                .when(residentService).archiveResident(RESIDENT_ID);

        mockMvc.perform(delete("/api/v1/residents/{id}/archive", RESIDENT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void archiveResident_returns403_forStaffRole() throws Exception {
        mockMvc.perform(delete("/api/v1/residents/{id}/archive", RESIDENT_ID))
                .andExpect(status().isForbidden());
    }

    // ── GET /residents ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void searchResidents_returns200_withNoFilters() throws Exception {
        when(residentService.searchResidents(null, null, null))
                .thenReturn(List.of(SAMPLE_SUMMARY));

        mockMvc.perform(get("/api/v1/residents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Alice Johnson"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void searchResidents_returns200_forStaffRole() throws Exception {
        when(residentService.searchResidents(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/residents"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void searchResidents_passesQueryParamsToService() throws Exception {
        when(residentService.searchResidents("Alice", "101", Resident.CareLevel.LOW))
                .thenReturn(List.of(SAMPLE_SUMMARY));

        mockMvc.perform(get("/api/v1/residents")
                        .param("name", "Alice")
                        .param("roomNumber", "101")
                        .param("careLevel", "LOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomNumber").value("101"));
    }

    // ── POST /residents/{id}/family-members ───────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void linkFamilyMember_returns204_onSuccess() throws Exception {
        doNothing().when(residentService).linkFamilyMember(any(), any(), any());

        LinkFamilyMemberRequest body = LinkFamilyMemberRequest.builder()
                .userId(USER_ID).relationshipLabel("Son").build();

        mockMvc.perform(post("/api/v1/residents/{id}/family-members", RESIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void linkFamilyMember_returns400_whenAlreadyLinked() throws Exception {
        doThrow(new BusinessException("Already linked"))
                .when(residentService).linkFamilyMember(any(), any(), any());

        LinkFamilyMemberRequest body = LinkFamilyMemberRequest.builder()
                .userId(USER_ID).relationshipLabel("Son").build();

        mockMvc.perform(post("/api/v1/residents/{id}/family-members", RESIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void linkFamilyMember_returns403_forStaffRole() throws Exception {
        LinkFamilyMemberRequest body = LinkFamilyMemberRequest.builder()
                .userId(USER_ID).build();

        mockMvc.perform(post("/api/v1/residents/{id}/family-members", RESIDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /residents/{id}/family-members/{userId} ────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void unlinkFamilyMember_returns204_onSuccess() throws Exception {
        doNothing().when(residentService).unlinkFamilyMember(RESIDENT_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/residents/{id}/family-members/{userId}",
                        RESIDENT_ID, USER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void unlinkFamilyMember_returns404_whenLinkNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("ResidentFamilyMember", USER_ID))
                .when(residentService).unlinkFamilyMember(RESIDENT_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/residents/{id}/family-members/{userId}",
                        RESIDENT_ID, USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void unlinkFamilyMember_returns403_forStaffRole() throws Exception {
        mockMvc.perform(delete("/api/v1/residents/{id}/family-members/{userId}",
                        RESIDENT_ID, USER_ID))
                .andExpect(status().isForbidden());
    }
}
