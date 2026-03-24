package com.lifeenrichment.controller;

import com.lifeenrichment.dto.request.CreateResidentRequest;
import com.lifeenrichment.dto.request.LinkFamilyMemberRequest;
import com.lifeenrichment.dto.request.UpdateResidentRequest;
import com.lifeenrichment.dto.response.ResidentResponse;
import com.lifeenrichment.dto.response.ResidentSummaryResponse;
import com.lifeenrichment.entity.Resident;
import com.lifeenrichment.service.ResidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/residents")
@RequiredArgsConstructor
@Tag(name = "Residents", description = "Manage resident profiles, care levels, and family member links")
public class ResidentController {

    private final ResidentService residentService;

    @Operation(summary = "Create a resident profile")
    @ApiResponse(responseCode = "201", description = "Resident created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping
    public ResponseEntity<ResidentResponse> createResident(@Valid @RequestBody CreateResidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residentService.createResident(request));
    }

    @Operation(summary = "Get a resident profile by ID")
    @ApiResponse(responseCode = "200", description = "Resident found")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Resident not found or archived")
    @PreAuthorize("hasAnyRole('DIRECTOR','STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ResidentResponse> getResident(@PathVariable UUID id) {
        return ResponseEntity.ok(residentService.getResident(id));
    }

    @Operation(summary = "Update a resident profile")
    @ApiResponse(responseCode = "200", description = "Resident updated")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Resident not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PutMapping("/{id}")
    public ResponseEntity<ResidentResponse> updateResident(@PathVariable UUID id,
                                                           @Valid @RequestBody UpdateResidentRequest request) {
        return ResponseEntity.ok(residentService.updateResident(id, request));
    }

    @Operation(summary = "Archive a resident (soft delete)")
    @ApiResponse(responseCode = "204", description = "Resident archived")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Resident not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @DeleteMapping("/{id}/archive")
    public ResponseEntity<Void> archiveResident(@PathVariable UUID id) {
        residentService.archiveResident(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search and list active residents")
    @ApiResponse(responseCode = "200", description = "List of active residents")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasAnyRole('DIRECTOR','STAFF')")
    @GetMapping
    public ResponseEntity<List<ResidentSummaryResponse>> searchResidents(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) Resident.CareLevel careLevel) {
        return ResponseEntity.ok(residentService.searchResidents(name, roomNumber, careLevel));
    }

    @Operation(summary = "Link a family member to a resident")
    @ApiResponse(responseCode = "204", description = "Family member linked")
    @ApiResponse(responseCode = "400", description = "Already linked or user not found")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Resident not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping("/{id}/family-members")
    public ResponseEntity<Void> linkFamilyMember(@PathVariable UUID id,
                                                 @Valid @RequestBody LinkFamilyMemberRequest request) {
        residentService.linkFamilyMember(id, request.getUserId(), request.getRelationshipLabel());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unlink a family member from a resident")
    @ApiResponse(responseCode = "204", description = "Family member unlinked")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Link not found")
    @PreAuthorize("hasRole('DIRECTOR')")
    @DeleteMapping("/{id}/family-members/{userId}")
    public ResponseEntity<Void> unlinkFamilyMember(@PathVariable UUID id,
                                                   @PathVariable UUID userId) {
        residentService.unlinkFamilyMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
