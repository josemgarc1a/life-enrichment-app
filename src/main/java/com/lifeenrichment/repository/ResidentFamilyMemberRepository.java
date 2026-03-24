package com.lifeenrichment.repository;

import com.lifeenrichment.entity.ResidentFamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResidentFamilyMemberRepository extends JpaRepository<ResidentFamilyMember, UUID> {

    List<ResidentFamilyMember> findAllByResidentId(UUID residentId);

    List<ResidentFamilyMember> findAllByUserId(UUID userId);

    void deleteByResidentIdAndUserId(UUID residentId, UUID userId);
}
