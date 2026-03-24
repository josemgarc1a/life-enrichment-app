package com.lifeenrichment.repository;

import com.lifeenrichment.entity.Resident;
import com.lifeenrichment.entity.ResidentFamilyMember;
import com.lifeenrichment.entity.User;
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
class ResidentFamilyMemberRepositoryTest {

    @Autowired
    private ResidentFamilyMemberRepository residentFamilyMemberRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Resident residentAlice;
    private Resident residentBob;
    private User familyMember1;
    private User familyMember2;

    @BeforeEach
    void setUp() {
        residentFamilyMemberRepository.deleteAll();
        residentRepository.deleteAll();
        userRepository.deleteAll();

        residentAlice = residentRepository.save(Resident.builder()
                .firstName("Alice")
                .lastName("Johnson")
                .dateOfBirth(LocalDate.of(1935, 4, 12))
                .roomNumber("101")
                .careLevel(Resident.CareLevel.LOW)
                .build());

        residentBob = residentRepository.save(Resident.builder()
                .firstName("Bob")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1940, 8, 3))
                .roomNumber("102")
                .careLevel(Resident.CareLevel.MEDIUM)
                .build());

        familyMember1 = userRepository.save(User.builder()
                .email("son@example.com")
                .passwordHash("$2a$10$hash1")
                .role(User.Role.FAMILY_MEMBER)
                .build());

        familyMember2 = userRepository.save(User.builder()
                .email("daughter@example.com")
                .passwordHash("$2a$10$hash2")
                .role(User.Role.FAMILY_MEMBER)
                .build());

        residentFamilyMemberRepository.save(ResidentFamilyMember.builder()
                .resident(residentAlice)
                .user(familyMember1)
                .relationshipLabel("Son")
                .build());

        residentFamilyMemberRepository.save(ResidentFamilyMember.builder()
                .resident(residentAlice)
                .user(familyMember2)
                .relationshipLabel("Daughter")
                .build());

        residentFamilyMemberRepository.save(ResidentFamilyMember.builder()
                .resident(residentBob)
                .user(familyMember1)
                .relationshipLabel("Son")
                .build());
    }

    // -------------------------------------------------------
    // findAllByResidentId
    // -------------------------------------------------------

    @Test
    void findAllByResidentId_returnsAllLinksForResident() {
        List<ResidentFamilyMember> result =
                residentFamilyMemberRepository.findAllByResidentId(residentAlice.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(r -> r.getUser().getEmail())
                .containsExactlyInAnyOrder("son@example.com", "daughter@example.com");
    }

    @Test
    void findAllByResidentId_returnsEmpty_whenNoLinks() {
        Resident unlinked = residentRepository.save(Resident.builder()
                .firstName("Carol")
                .lastName("White")
                .dateOfBirth(LocalDate.of(1928, 1, 1))
                .roomNumber("103")
                .careLevel(Resident.CareLevel.HIGH)
                .build());

        List<ResidentFamilyMember> result =
                residentFamilyMemberRepository.findAllByResidentId(unlinked.getId());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------
    // findAllByUserId
    // -------------------------------------------------------

    @Test
    void findAllByUserId_returnsAllResidentsLinkedToUser() {
        List<ResidentFamilyMember> result =
                residentFamilyMemberRepository.findAllByUserId(familyMember1.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(r -> r.getResident().getFirstName())
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void findAllByUserId_returnsEmpty_whenUserHasNoLinks() {
        User unlinkedUser = userRepository.save(User.builder()
                .email("unlinked@example.com")
                .passwordHash("$2a$10$hash3")
                .role(User.Role.FAMILY_MEMBER)
                .build());

        List<ResidentFamilyMember> result =
                residentFamilyMemberRepository.findAllByUserId(unlinkedUser.getId());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------
    // deleteByResidentIdAndUserId
    // -------------------------------------------------------

    @Test
    void deleteByResidentIdAndUserId_removesTheLink() {
        residentFamilyMemberRepository.deleteByResidentIdAndUserId(
                residentAlice.getId(), familyMember1.getId());
        entityManager.flush();

        List<ResidentFamilyMember> remaining =
                residentFamilyMemberRepository.findAllByResidentId(residentAlice.getId());

        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getUser().getEmail()).isEqualTo("daughter@example.com");
    }

    @Test
    void deleteByResidentIdAndUserId_doesNotAffectOtherLinks() {
        residentFamilyMemberRepository.deleteByResidentIdAndUserId(
                residentAlice.getId(), familyMember1.getId());
        entityManager.flush();

        List<ResidentFamilyMember> bobLinks =
                residentFamilyMemberRepository.findAllByResidentId(residentBob.getId());

        assertThat(bobLinks).hasSize(1);
    }

    // -------------------------------------------------------
    // Unique constraint
    // -------------------------------------------------------

    @Test
    void save_throwsException_whenDuplicateLinkAttempted() {
        ResidentFamilyMember duplicate = ResidentFamilyMember.builder()
                .resident(residentAlice)
                .user(familyMember1)
                .relationshipLabel("Son")
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> {
                    residentFamilyMemberRepository.saveAndFlush(duplicate);
                }
        );
    }

    // -------------------------------------------------------
    // Entity field integrity
    // -------------------------------------------------------

    @Test
    void save_populatesLinkedAtTimestamp() {
        ResidentFamilyMember saved = residentFamilyMemberRepository.saveAndFlush(
                ResidentFamilyMember.builder()
                        .resident(residentBob)
                        .user(familyMember2)
                        .relationshipLabel("Daughter")
                        .build());

        entityManager.refresh(saved);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLinkedAt()).isNotNull();
        assertThat(saved.getRelationshipLabel()).isEqualTo("Daughter");
    }
}
