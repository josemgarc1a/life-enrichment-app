package com.lifeenrichment.repository;

import com.lifeenrichment.entity.Resident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Resident} entities.
 *
 * <p>All query methods that return resident lists exclude archived records by filtering on
 * {@code is_active = true}, ensuring archived residents do not surface in the application UI.
 */
@Repository
public interface ResidentRepository extends JpaRepository<Resident, UUID> {

    /** Returns all residents whose {@code isActive} flag is {@code true}. */
    List<Resident> findAllByIsActiveTrue();

    /**
     * Case-insensitive substring search across both first and last name.
     * Used when a name filter is supplied on the search endpoint; note that archived
     * residents are <em>included</em> in this query — callers should pre-filter if needed.
     */
    List<Resident> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName);

    /** Returns active residents assigned to the given room number. */
    List<Resident> findAllByIsActiveTrueAndRoomNumber(String roomNumber);

    /** Returns active residents at the specified care level. */
    List<Resident> findAllByIsActiveTrueAndCareLevel(Resident.CareLevel careLevel);
}
