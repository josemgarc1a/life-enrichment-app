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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResidentService {

    private final ResidentRepository residentRepository;
    private final ResidentFamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public ResidentResponse createResident(CreateResidentRequest request) {
        log.info("Creating resident: {} {}", request.getFirstName(), request.getLastName());

        Resident resident = Resident.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .roomNumber(request.getRoomNumber())
                .careLevel(request.getCareLevel())
                .preferences(request.getPreferences())
                .build();

        Resident saved = residentRepository.save(resident);
        log.info("Resident created with id: {}", saved.getId());
        return toResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResidentResponse getResident(UUID id) {
        Resident resident = residentRepository.findById(id)
                .filter(Resident::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Resident", id));
        return toResponse(resident);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public ResidentResponse updateResident(UUID id, UpdateResidentRequest request) {
        log.info("Updating resident id: {}", id);

        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident", id));

        if (request.getFirstName() != null)   resident.setFirstName(request.getFirstName());
        if (request.getLastName() != null)    resident.setLastName(request.getLastName());
        if (request.getDateOfBirth() != null) resident.setDateOfBirth(request.getDateOfBirth());
        if (request.getRoomNumber() != null)  resident.setRoomNumber(request.getRoomNumber());
        if (request.getCareLevel() != null)   resident.setCareLevel(request.getCareLevel());
        if (request.getPreferences() != null) resident.setPreferences(request.getPreferences());

        Resident saved = residentRepository.save(resident);
        log.info("Resident updated: {}", id);
        return toResponse(saved);
    }

    // ── Archive ───────────────────────────────────────────────────────────────

    @Transactional
    public void archiveResident(UUID id) {
        log.info("Archiving resident id: {}", id);

        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident", id));

        resident.setActive(false);
        residentRepository.save(resident);
        log.info("Resident archived: {}", id);
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ResidentSummaryResponse> searchResidents(String name, String roomNumber,
                                                         Resident.CareLevel careLevel) {
        List<Resident> residents = residentRepository.findAllByIsActiveTrue();

        if (name != null && !name.isBlank()) {
            String lower = name.toLowerCase();
            residents = residents.stream()
                    .filter(r -> r.getFirstName().toLowerCase().contains(lower)
                              || r.getLastName().toLowerCase().contains(lower))
                    .toList();
        }

        if (roomNumber != null && !roomNumber.isBlank()) {
            residents = residents.stream()
                    .filter(r -> roomNumber.equalsIgnoreCase(r.getRoomNumber()))
                    .toList();
        }

        if (careLevel != null) {
            residents = residents.stream()
                    .filter(r -> careLevel == r.getCareLevel())
                    .toList();
        }

        return residents.stream().map(this::toSummary).toList();
    }

    // ── Family member linking ─────────────────────────────────────────────────

    @Transactional
    public void linkFamilyMember(UUID residentId, UUID userId, String relationshipLabel) {
        Resident resident = residentRepository.findById(residentId)
                .orElseThrow(() -> new ResourceNotFoundException("Resident", residentId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        boolean alreadyLinked = familyMemberRepository.findAllByResidentId(residentId)
                .stream().anyMatch(l -> l.getUser().getId().equals(userId));

        if (alreadyLinked) {
            throw new BusinessException("User " + userId + " is already linked to resident " + residentId);
        }

        familyMemberRepository.save(ResidentFamilyMember.builder()
                .resident(resident)
                .user(user)
                .relationshipLabel(relationshipLabel)
                .build());

        log.info("Linked user {} to resident {} as '{}'", userId, residentId, relationshipLabel);
    }

    @Transactional
    public void unlinkFamilyMember(UUID residentId, UUID userId) {
        boolean exists = familyMemberRepository.findAllByResidentId(residentId)
                .stream().anyMatch(l -> l.getUser().getId().equals(userId));

        if (!exists) {
            throw new ResourceNotFoundException("ResidentFamilyMember",
                    "residentId=" + residentId + " userId=" + userId);
        }

        familyMemberRepository.deleteByResidentIdAndUserId(residentId, userId);
        log.info("Unlinked user {} from resident {}", userId, residentId);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ResidentResponse toResponse(Resident resident) {
        List<ResidentResponse.FamilyMemberEntry> familyMembers =
                familyMemberRepository.findAllByResidentId(resident.getId()).stream()
                        .map(link -> ResidentResponse.FamilyMemberEntry.builder()
                                .userId(link.getUser().getId())
                                .fullName(link.getUser().getEmail())
                                .email(link.getUser().getEmail())
                                .relationshipLabel(link.getRelationshipLabel())
                                .build())
                        .toList();

        return ResidentResponse.builder()
                .id(resident.getId())
                .firstName(resident.getFirstName())
                .lastName(resident.getLastName())
                .dateOfBirth(resident.getDateOfBirth())
                .roomNumber(resident.getRoomNumber())
                .careLevel(resident.getCareLevel())
                .preferences(resident.getPreferences())
                .photoUrl(resident.getPhotoUrl())
                .isActive(resident.isActive())
                .createdAt(resident.getCreatedAt())
                .updatedAt(resident.getUpdatedAt())
                .familyMembers(familyMembers)
                .build();
    }

    private ResidentSummaryResponse toSummary(Resident resident) {
        return ResidentSummaryResponse.builder()
                .id(resident.getId())
                .fullName(resident.getFirstName() + " " + resident.getLastName())
                .roomNumber(resident.getRoomNumber())
                .careLevel(resident.getCareLevel())
                .photoUrl(resident.getPhotoUrl())
                .isActive(resident.isActive())
                .build();
    }
}
