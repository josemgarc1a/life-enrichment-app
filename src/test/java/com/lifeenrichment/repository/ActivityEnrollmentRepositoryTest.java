package com.lifeenrichment.repository;

import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.entity.ActivityEnrollment;
import com.lifeenrichment.entity.Resident;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class ActivityEnrollmentRepositoryTest {

    @Autowired
    private ActivityEnrollmentRepository enrollmentRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private EntityManager entityManager;

    private Activity yogaActivity;
    private Activity artsActivity;
    private Resident alice;
    private Resident bob;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 10, 0);

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        residentRepository.deleteAll();

        // Persist activities directly via EntityManager (ActivityRepository is on a separate branch)
        yogaActivity = Activity.builder()
                .title("Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(10)
                .status(Activity.Status.SCHEDULED)
                .build();
        entityManager.persist(yogaActivity);

        artsActivity = Activity.builder()
                .title("Watercolour Class")
                .category(Activity.Category.ARTS)
                .location("Arts Room")
                .startTime(NOW.plusDays(2))
                .endTime(NOW.plusDays(2).plusHours(2))
                .capacity(8)
                .status(Activity.Status.SCHEDULED)
                .build();
        entityManager.persist(artsActivity);

        alice = residentRepository.save(Resident.builder()
                .firstName("Alice").lastName("Johnson")
                .roomNumber("101").careLevel(Resident.CareLevel.LOW)
                .isActive(true).build());

        bob = residentRepository.save(Resident.builder()
                .firstName("Bob").lastName("Smith")
                .roomNumber("102").careLevel(Resident.CareLevel.MEDIUM)
                .isActive(true).build());

        entityManager.flush();

        // Alice enrolled in both activities; Bob only in yoga
        enrollmentRepository.save(ActivityEnrollment.builder()
                .activity(yogaActivity).resident(alice).build());
        enrollmentRepository.save(ActivityEnrollment.builder()
                .activity(artsActivity).resident(alice).build());
        enrollmentRepository.save(ActivityEnrollment.builder()
                .activity(yogaActivity).resident(bob).build());
    }

    // -------------------------------------------------------
    // findByActivityId
    // -------------------------------------------------------

    @Test
    void findByActivityId_returnsAllEnrolledResidents() {
        List<ActivityEnrollment> roster = enrollmentRepository.findByActivityId(yogaActivity.getId());

        assertThat(roster).hasSize(2);
        assertThat(roster).extracting(e -> e.getResident().getFirstName())
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void findByActivityId_returnsEmpty_whenNoEnrollments() {
        Activity emptyActivity = Activity.builder()
                .title("Empty Activity")
                .category(Activity.Category.SOCIAL)
                .location("Room A")
                .startTime(NOW.plusDays(5))
                .endTime(NOW.plusDays(5).plusHours(1))
                .capacity(5)
                .status(Activity.Status.SCHEDULED)
                .build();
        entityManager.persist(emptyActivity);
        entityManager.flush();

        List<ActivityEnrollment> roster = enrollmentRepository.findByActivityId(emptyActivity.getId());

        assertThat(roster).isEmpty();
    }

    // -------------------------------------------------------
    // findByResidentId
    // -------------------------------------------------------

    @Test
    void findByResidentId_returnsAllActivitiesForResident() {
        List<ActivityEnrollment> schedule = enrollmentRepository.findByResidentId(alice.getId());

        assertThat(schedule).hasSize(2);
        assertThat(schedule).extracting(e -> e.getActivity().getTitle())
                .containsExactlyInAnyOrder("Morning Yoga", "Watercolour Class");
    }

    @Test
    void findByResidentId_returnsSingleActivity_whenEnrolledInOne() {
        List<ActivityEnrollment> schedule = enrollmentRepository.findByResidentId(bob.getId());

        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).getActivity().getTitle()).isEqualTo("Morning Yoga");
    }

    // -------------------------------------------------------
    // countByActivityId
    // -------------------------------------------------------

    @Test
    void countByActivityId_returnsCorrectEnrollmentCount() {
        long count = enrollmentRepository.countByActivityId(yogaActivity.getId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countByActivityId_returnsZero_whenNoEnrollments() {
        Activity emptyActivity = Activity.builder()
                .title("Empty Activity")
                .category(Activity.Category.COGNITIVE)
                .location("Library")
                .startTime(NOW.plusDays(5))
                .endTime(NOW.plusDays(5).plusHours(1))
                .capacity(6)
                .status(Activity.Status.SCHEDULED)
                .build();
        entityManager.persist(emptyActivity);
        entityManager.flush();

        long count = enrollmentRepository.countByActivityId(emptyActivity.getId());

        assertThat(count).isZero();
    }

    // -------------------------------------------------------
    // existsByActivityIdAndResidentId
    // -------------------------------------------------------

    @Test
    void existsByActivityIdAndResidentId_returnsTrue_whenEnrolled() {
        boolean exists = enrollmentRepository.existsByActivityIdAndResidentId(
                yogaActivity.getId(), alice.getId());

        assertThat(exists).isTrue();
    }

    @Test
    void existsByActivityIdAndResidentId_returnsFalse_whenNotEnrolled() {
        boolean exists = enrollmentRepository.existsByActivityIdAndResidentId(
                artsActivity.getId(), bob.getId());

        assertThat(exists).isFalse();
    }

    // -------------------------------------------------------
    // deleteByActivityIdAndResidentId
    // -------------------------------------------------------

    @Test
    void deleteByActivityIdAndResidentId_removesEnrollment() {
        enrollmentRepository.deleteByActivityIdAndResidentId(yogaActivity.getId(), alice.getId());

        boolean stillExists = enrollmentRepository.existsByActivityIdAndResidentId(
                yogaActivity.getId(), alice.getId());
        assertThat(stillExists).isFalse();
        assertThat(enrollmentRepository.countByActivityId(yogaActivity.getId())).isEqualTo(1);
    }

    @Test
    void deleteByActivityIdAndResidentId_doesNotAffectOtherEnrollments() {
        enrollmentRepository.deleteByActivityIdAndResidentId(yogaActivity.getId(), alice.getId());

        // Bob's enrollment should remain
        assertThat(enrollmentRepository.existsByActivityIdAndResidentId(
                yogaActivity.getId(), bob.getId())).isTrue();

        // Alice's arts enrollment should remain
        assertThat(enrollmentRepository.existsByActivityIdAndResidentId(
                artsActivity.getId(), alice.getId())).isTrue();
    }

    // -------------------------------------------------------
    // Unique constraint
    // -------------------------------------------------------

    @Test
    void save_throwsException_onDuplicateEnrollment() {
        assertThrows(Exception.class, () -> {
            enrollmentRepository.saveAndFlush(ActivityEnrollment.builder()
                    .activity(yogaActivity)
                    .resident(alice)
                    .build());
        });
    }

    // -------------------------------------------------------
    // Entity field integrity
    // -------------------------------------------------------

    @Test
    void save_populatesEnrolledAt_automatically() {
        Resident carol = residentRepository.save(Resident.builder()
                .firstName("Carol").lastName("White")
                .roomNumber("103").careLevel(Resident.CareLevel.HIGH)
                .isActive(true).build());

        ActivityEnrollment saved = enrollmentRepository.saveAndFlush(ActivityEnrollment.builder()
                .activity(artsActivity)
                .resident(carol)
                .build());

        entityManager.refresh(saved);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEnrolledAt()).isNotNull();
        assertThat(saved.getEnrolledBy()).isNull();
    }
}
