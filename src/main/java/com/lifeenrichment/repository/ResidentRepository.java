package com.lifeenrichment.repository;

import com.lifeenrichment.entity.Resident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResidentRepository extends JpaRepository<Resident, UUID> {

    List<Resident> findAllByIsActiveTrue();

    List<Resident> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName);

    List<Resident> findAllByIsActiveTrueAndRoomNumber(String roomNumber);

    List<Resident> findAllByIsActiveTrueAndCareLevel(Resident.CareLevel careLevel);
}
