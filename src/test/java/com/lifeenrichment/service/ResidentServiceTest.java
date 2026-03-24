package com.lifeenrichment.service;

import com.lifeenrichment.dto.request.CreateResidentRequest;
import com.lifeenrichment.dto.request.UpdateResidentRequest;
import com.lifeenrichment.dto.response.ResidentResponse;
import com.lifeenrichment.dto.response.ResidentSummaryResponse;
import com.lifeenrichment.entity.Resident;
import com.lifeenrichment.entity.ResidentFamilyMember;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.exception.ResourceNotFoundException;
import com.lifeenrichment.repository.ResidentFamilyMemberRepository;
import com.lifeenrichment.repository.ResidentRepository;
import com.lifeenrichment.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResidentServiceTest {

    @Mock private ResidentRepository residentRepository;
    @Mock private ResidentFamilyMemberRepository familyMemberRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ResidentService residentService;

    private UUID residentId;
    private UUID userId;
    private Resident activeResident;
    private Resident archivedResident;
    private User familyUser;

    @BeforeEach
    void setUp() {
        residentId = UUID.randomUUID();
        userId = UUID.randomUUID();

        activeResident = Resident.builder()
                .id(residentId)
                .firstName("Alice")
                .lastName("Johnson")
                .dateOfBirth(LocalDate.of(1935, 4, 12))
                .roomNumber("101")
                .careLevel(Resident.CareLevel.LOW)
                .isActive(true)
                .build();

        archivedResident = Resident.builder()
                .id(residentId)
                .firstName("Alice")
                .lastName("Johnson")
                .isActive(false)
                .build();

        familyUser = User.builder()
                .id(userId)
                .email("son@example.com")
                .passwordHash("hash")
                .role(User.Role.FAMILY_MEMBER)
                .build();
    }

    // ── createResident ────────────────────────────────────────────────────────

    @Test
    void createResident_savesAndReturnsResponse() {
        CreateResidentRequest request = CreateResidentRequest.builder()
                .firstName("Alice")
                .lastName("Johnson")
                .dateOfBirth(LocalDate.of(1935, 4, 12))
                .roomNumber("101")
                .careLevel(Resident.CareLevel.LOW)
                .build();

        when(residentRepository.save(any())).thenReturn(activeResident);
        when(familyMemberRepository.findAllByResidentId(any())).thenReturn(List.of());

        ResidentResponse response = residentService.createResident(request);

        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getLastName()).isEqualTo("Johnson");
        assertThat(response.getRoomNumber()).isEqualTo("101");
        verify(residentRepository).save(any(Resident.class));
    }

    // ── getResident ───────────────────────────────────────────────────────────

    @Test
    void getResident_returnsResponse_whenActiveAndFound() {
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(activeResident));
        when(familyMemberRepository.findAllByResidentId(residentId)).thenReturn(List.of());

        ResidentResponse response = residentService.getResident(residentId);

        assertThat(response.getId()).isEqualTo(residentId);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void getResident_throwsResourceNotFoundException_whenNotFound() {
        when(residentRepository.findById(residentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> residentService.getResident(residentId));
    }

    @Test
    void getResident_throwsResourceNotFoundException_whenArchived() {
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(archivedResident));

        assertThrows(ResourceNotFoundException.class,
                () -> residentService.getResident(residentId));
    }

    // ── updateResident ────────────────────────────────────────────────────────

    @Test
    void updateResident_appliesPartialUpdate_andReturnsResponse() {
        UpdateResidentRequest request = UpdateResidentRequest.builder()
                .roomNumber("202")
                .careLevel(Resident.CareLevel.HIGH)
                .build();

        when(residentRepository.findById(residentId)).thenReturn(Optional.of(activeResident));
        when(residentRepository.save(any())).thenReturn(activeResident);
        when(familyMemberRepository.findAllByResidentId(any())).thenReturn(List.of());

        residentService.updateResident(residentId, request);

        assertThat(activeResident.getRoomNumber()).isEqualTo("202");
        assertThat(activeResident.getCareLevel()).isEqualTo(Resident.CareLevel.HIGH);
        assertThat(activeResident.getFirstName()).isEqualTo("Alice"); // unchanged
        verify(residentRepository).save(activeResident);
    }

    @Test
    void updateResident_throwsResourceNotFoundException_whenNotFound() {
        when(residentRepository.findById(residentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> residentService.updateResident(residentId, UpdateResidentRequest.builder().build()));
    }

    // ── archiveResident ───────────────────────────────────────────────────────

    @Test
    void archiveResident_setsIsActiveFalse_andDoesNotDelete() {
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(activeResident));
        when(residentRepository.save(any())).thenReturn(activeResident);

        residentService.archiveResident(residentId);

        assertThat(activeResident.isActive()).isFalse();
        verify(residentRepository).save(activeResident);
        verify(residentRepository, never()).delete(any());
    }

    @Test
    void archiveResident_throwsResourceNotFoundException_whenNotFound() {
        when(residentRepository.findById(residentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> residentService.archiveResident(residentId));
    }

    // ── searchResidents ───────────────────────────────────────────────────────

    @Test
    void searchResidents_returnsAll_whenNoFilters() {
        when(residentRepository.findAllByIsActiveTrue()).thenReturn(List.of(activeResident));

        List<ResidentSummaryResponse> result =
                residentService.searchResidents(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFullName()).isEqualTo("Alice Johnson");
    }

    @Test
    void searchResidents_filtersByName_caseInsensitive() {
        Resident bob = Resident.builder().id(UUID.randomUUID())
                .firstName("Bob").lastName("Smith")
                .roomNumber("102").careLevel(Resident.CareLevel.MEDIUM).isActive(true).build();

        when(residentRepository.findAllByIsActiveTrue()).thenReturn(List.of(activeResident, bob));

        List<ResidentSummaryResponse> result =
                residentService.searchResidents("alice", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFullName()).isEqualTo("Alice Johnson");
    }

    @Test
    void searchResidents_filtersByRoomNumber() {
        Resident bob = Resident.builder().id(UUID.randomUUID())
                .firstName("Bob").lastName("Smith")
                .roomNumber("102").careLevel(Resident.CareLevel.MEDIUM).isActive(true).build();

        when(residentRepository.findAllByIsActiveTrue()).thenReturn(List.of(activeResident, bob));

        List<ResidentSummaryResponse> result =
                residentService.searchResidents(null, "102", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoomNumber()).isEqualTo("102");
    }

    @Test
    void searchResidents_filtersByCareLevel() {
        when(residentRepository.findAllByIsActiveTrue()).thenReturn(List.of(activeResident));

        List<ResidentSummaryResponse> result =
                residentService.searchResidents(null, null, Resident.CareLevel.MEDIUM);

        assertThat(result).isEmpty();
    }

    // ── linkFamilyMember ──────────────────────────────────────────────────────

    @Test
    void linkFamilyMember_savesLink_whenNotAlreadyLinked() {
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(activeResident));
        when(userRepository.findById(userId)).thenReturn(Optional.of(familyUser));
        when(familyMemberRepository.findAllByResidentId(residentId)).thenReturn(List.of());

        residentService.linkFamilyMember(residentId, userId, "Son");

        verify(familyMemberRepository).save(any(ResidentFamilyMember.class));
    }

    @Test
    void linkFamilyMember_throwsBusinessException_whenAlreadyLinked() {
        ResidentFamilyMember existing = ResidentFamilyMember.builder()
                .resident(activeResident).user(familyUser).build();

        when(residentRepository.findById(residentId)).thenReturn(Optional.of(activeResident));
        when(userRepository.findById(userId)).thenReturn(Optional.of(familyUser));
        when(familyMemberRepository.findAllByResidentId(residentId)).thenReturn(List.of(existing));

        assertThrows(BusinessException.class,
                () -> residentService.linkFamilyMember(residentId, userId, "Son"));
    }

    @Test
    void linkFamilyMember_throwsResourceNotFoundException_whenResidentNotFound() {
        when(residentRepository.findById(residentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> residentService.linkFamilyMember(residentId, userId, "Son"));
    }

    // ── unlinkFamilyMember ────────────────────────────────────────────────────

    @Test
    void unlinkFamilyMember_deletesLink_whenExists() {
        ResidentFamilyMember existing = ResidentFamilyMember.builder()
                .resident(activeResident).user(familyUser).build();

        when(familyMemberRepository.findAllByResidentId(residentId)).thenReturn(List.of(existing));

        residentService.unlinkFamilyMember(residentId, userId);

        verify(familyMemberRepository).deleteByResidentIdAndUserId(residentId, userId);
    }

    @Test
    void unlinkFamilyMember_throwsResourceNotFoundException_whenLinkDoesNotExist() {
        when(familyMemberRepository.findAllByResidentId(residentId)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> residentService.unlinkFamilyMember(residentId, userId));
    }
}
