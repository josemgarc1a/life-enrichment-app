package com.lifeenrichment.repository;

import com.lifeenrichment.entity.ResidentFamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ResidentFamilyMember} join entities.
 *
 * <p>Provides lookups by resident or by user, and a single-link deletion
 * used when a Director unlinks a family member from a resident.
 */
@Repository
public interface ResidentFamilyMemberRepository extends JpaRepository<ResidentFamilyMember, UUID> {

    /** Returns all family-member links associated with the given resident. */
    List<ResidentFamilyMember> findAllByResidentId(UUID residentId);

    /** Returns all residents a given user account is linked to as a family member. */
    List<ResidentFamilyMember> findAllByUserId(UUID userId);

    /** Removes the link between a specific resident and a specific user. */
    void deleteByResidentIdAndUserId(UUID residentId, UUID userId);
}
