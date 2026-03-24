package com.lifeenrichment.service;

import com.lifeenrichment.dto.request.CreateResidentRequest;
import com.lifeenrichment.dto.request.UpdateResidentRequest;
import com.lifeenrichment.dto.response.PhotoUploadResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic for managing resident profiles, family member links, and profile photos.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li><strong>Soft delete only</strong> — residents are never removed from the database;
 *       archiving sets {@code isActive = false} to preserve historical records.</li>
 *   <li><strong>In-memory search filtering</strong> — all active residents are fetched once
 *       and then filtered in Java, keeping queries simple until data volumes require JPQL.</li>
 *   <li><strong>Photo management</strong> — when a new photo is uploaded, the previous S3
 *       object is deleted first to prevent orphaned files.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResidentService {

    private static final long MAX_PHOTO_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final ResidentRepository residentRepository;
    private final ResidentFamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates and persists a new resident profile with {@code isActive = true}.
     *
     * @return the full profile response including the generated UUID
     */
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

    /**
     * Retrieves an active resident's full profile, including linked family members.
     *
     * @throws ResourceNotFoundException if no resident exists with that ID, or if the resident is archived
     */
    @Transactional(readOnly = true)
    public ResidentResponse getResident(UUID id) {
        Resident resident = residentRepository.findById(id)
                .filter(Resident::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Resident", id));
        return toResponse(resident);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Applies a partial update to a resident profile — only non-null fields in the request
     * overwrite the existing values; omitted fields are left unchanged.
     *
     * @throws ResourceNotFoundException if no resident exists with that ID
     */
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

    /**
     * Soft-deletes a resident by setting {@code isActive = false}.
     * The record and all associated family member links are retained in the database.
     *
     * @throws ResourceNotFoundException if no resident exists with that ID
     */
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

    /**
     * Returns a filtered list of active residents. All filter parameters are optional;
     * passing {@code null} for all three returns every active resident.
     *
     * @param name       case-insensitive substring match against first or last name
     * @param roomNumber exact, case-insensitive room number match
     * @param careLevel  exact care-level match
     */
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

    /**
     * Creates a link between an active resident and a user with the {@code FAMILY_MEMBER} role.
     *
     * @param residentId        the resident to link to
     * @param userId            the family member user account to associate
     * @param relationshipLabel optional description of the relationship (e.g. "Son")
     * @throws ResourceNotFoundException if the resident or user does not exist
     * @throws BusinessException         if the user is already linked to this resident
     */
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

    /**
     * Removes the link between a resident and a family member user account.
     *
     * @throws ResourceNotFoundException if no such link exists
     */
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

    // ── Photo upload ──────────────────────────────────────────────────────────

    /**
     * Validates, uploads, and associates a profile photo for the given resident.
     *
     * <p>Validation rejects files larger than 5 MB or with MIME types other than
     * {@code image/jpeg}, {@code image/png}, or {@code image/webp}. If the resident
     * already has a photo, the existing S3 object is deleted before the new one is uploaded.
     *
     * @throws BusinessException         if the file is too large or has an unsupported MIME type
     * @throws ResourceNotFoundException if no resident exists with that ID
     */
    @Transactional
    public PhotoUploadResponse uploadPhoto(UUID id, MultipartFile file) {
        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new BusinessException("File exceeds maximum allowed size of 5 MB");
        }
        if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new BusinessException("Unsupported file type: " + file.getContentType()
                    + ". Allowed: image/jpeg, image/png, image/webp");
        }

        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident", id));

        if (resident.getPhotoUrl() != null) {
            String oldKey = extractS3Key(resident.getPhotoUrl());
            s3Service.deleteFile(oldKey);
        }

        String extension = getExtension(file.getOriginalFilename());
        String key = "residents/" + UUID.randomUUID() + extension;
        String url = s3Service.uploadFile(key, file);

        resident.setPhotoUrl(url);
        residentRepository.save(resident);

        log.info("Photo uploaded for resident {}: {}", id, url);
        return PhotoUploadResponse.builder().photoUrl(url).build();
    }

    private String extractS3Key(String url) {
        int idx = url.indexOf(".amazonaws.com/");
        return idx >= 0 ? url.substring(idx + ".amazonaws.com/".length()) : url;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
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
