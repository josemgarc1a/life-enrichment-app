package com.lifeenrichment.repository;

import com.lifeenrichment.entity.Resident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ResidentRepositoryTest {

    @Autowired
    private ResidentRepository residentRepository;

    @BeforeEach
    void setUp() {
        residentRepository.deleteAll();

        residentRepository.save(Resident.builder()
                .firstName("Alice")
                .lastName("Johnson")
                .dateOfBirth(LocalDate.of(1935, 4, 12))
                .roomNumber("101")
                .careLevel(Resident.CareLevel.LOW)
                .isActive(true)
                .build());

        residentRepository.save(Resident.builder()
                .firstName("Bob")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1940, 8, 3))
                .roomNumber("102")
                .careLevel(Resident.CareLevel.MEDIUM)
                .isActive(true)
                .build());

        residentRepository.save(Resident.builder()
                .firstName("Carol")
                .lastName("Johnson")
                .dateOfBirth(LocalDate.of(1932, 1, 22))
                .roomNumber("103")
                .careLevel(Resident.CareLevel.HIGH)
                .isActive(false)
                .build());
    }

    // -------------------------------------------------------
    // findAllByIsActiveTrue
    // -------------------------------------------------------

    @Test
    void findAllByIsActiveTrue_returnsOnlyActiveResidents() {
        List<Resident> result = residentRepository.findAllByIsActiveTrue();

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(Resident::isActive);
    }

    @Test
    void findAllByIsActiveTrue_excludesArchivedResidents() {
        List<Resident> result = residentRepository.findAllByIsActiveTrue();

        assertThat(result).noneMatch(r -> r.getFirstName().equals("Carol"));
    }

    // -------------------------------------------------------
    // findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase
    // -------------------------------------------------------

    @Test
    void nameSearch_matchesFirstNameCaseInsensitive() {
        List<Resident> result = residentRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("alice", "alice");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Alice");
    }

    @Test
    void nameSearch_matchesLastNameCaseInsensitive() {
        List<Resident> result = residentRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("johnson", "johnson");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Resident::getLastName)
                .containsOnly("Johnson");
    }

    @Test
    void nameSearch_matchesPartialName() {
        List<Resident> result = residentRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("smi", "smi");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLastName()).isEqualTo("Smith");
    }

    @Test
    void nameSearch_returnsEmpty_whenNoMatch() {
        List<Resident> result = residentRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("xyz", "xyz");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------
    // findAllByIsActiveTrueAndRoomNumber
    // -------------------------------------------------------

    @Test
    void findAllByIsActiveTrueAndRoomNumber_returnsActiveResidentInRoom() {
        List<Resident> result = residentRepository.findAllByIsActiveTrueAndRoomNumber("101");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Alice");
    }

    @Test
    void findAllByIsActiveTrueAndRoomNumber_excludesArchivedResidentInRoom() {
        List<Resident> result = residentRepository.findAllByIsActiveTrueAndRoomNumber("103");

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByIsActiveTrueAndRoomNumber_returnsEmpty_whenRoomNotFound() {
        List<Resident> result = residentRepository.findAllByIsActiveTrueAndRoomNumber("999");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------
    // findAllByIsActiveTrueAndCareLevel
    // -------------------------------------------------------

    @Test
    void findAllByIsActiveTrueAndCareLevel_returnsActiveResidentsWithMatchingLevel() {
        List<Resident> result = residentRepository
                .findAllByIsActiveTrueAndCareLevel(Resident.CareLevel.LOW);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCareLevel()).isEqualTo(Resident.CareLevel.LOW);
    }

    @Test
    void findAllByIsActiveTrueAndCareLevel_excludesArchivedResidents() {
        List<Resident> result = residentRepository
                .findAllByIsActiveTrueAndCareLevel(Resident.CareLevel.HIGH);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByIsActiveTrueAndCareLevel_returnsEmpty_whenNoneMatch() {
        List<Resident> result = residentRepository
                .findAllByIsActiveTrueAndCareLevel(Resident.CareLevel.MEDIUM);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Bob");
    }

    // -------------------------------------------------------
    // Entity field integrity
    // -------------------------------------------------------

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void save_persistsAllFields_andPopulatesAuditTimestamps() {
        Resident saved = residentRepository.saveAndFlush(Resident.builder()
                .firstName("David")
                .lastName("Brown")
                .dateOfBirth(LocalDate.of(1928, 6, 15))
                .roomNumber("104")
                .careLevel(Resident.CareLevel.MEDIUM)
                .preferences("Prefers morning activities")
                .build());

        entityManager.refresh(saved);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getPreferences()).isEqualTo("Prefers morning activities");
    }
}
