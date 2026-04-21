package com.lifeenrichment.repository;

import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.entity.AttendanceLog;
import com.lifeenrichment.entity.AttendanceLog.AssistanceLevel;
import com.lifeenrichment.entity.AttendanceLog.AttendanceStatus;
import com.lifeenrichment.entity.Resident;
import com.lifeenrichment.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class AttendanceLogRepositoryTest {

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 4, 1, 10, 0);

    private Activity yogaActivity;
    private Activity artsActivity;
    private Resident alice;
    private Resident bob;
    private User staffMember;

    @BeforeEach
    void setUp() {
        attendanceLogRepository.deleteAll();

        staffMember = userRepository.save(User.builder()
                .email("staff@facility.com")
                .passwordHash("$2a$10$hash")
                .role(User.Role.STAFF)
                .build());

        alice = residentRepository.save(Resident.builder()
                .firstName("Alice").lastName("Johnson")
                .roomNumber("101").careLevel(Resident.CareLevel.LOW)
                .isActive(true).build());

        bob = residentRepository.save(Resident.builder()
                .firstName("Bob").lastName("Smith")
                .roomNumber("102").careLevel(Resident.CareLevel.MEDIUM)
                .isActive(true).build());

        yogaActivity = Activity.builder()
                .title("Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(BASE_TIME)
                .endTime(BASE_TIME.plusHours(1))
                .capacity(10)
                .status(Activity.Status.COMPLETED)
                .build();
        entityManager.persist(yogaActivity);

        artsActivity = Activity.builder()
                .title("Watercolour Class")
                .category(Activity.Category.ARTS)
                .location("Arts Room")
                .startTime(BASE_TIME.plusDays(1))
                .endTime(BASE_TIME.plusDays(1).plusHours(2))
                .capacity(8)
                .status(Activity.Status.COMPLETED)
                .build();
        entityManager.persist(artsActivity);

        entityManager.flush();

        // Alice attended yoga (with moderate assistance) and was absent from arts
        attendanceLogRepository.save(AttendanceLog.builder()
                .activity(yogaActivity).resident(alice)
                .status(AttendanceStatus.ATTENDED)
                .assistanceLevel(AssistanceLevel.MODERATE)
                .assistanceNotes("Needed help with mat")
                .loggedBy(staffMember)
                .loggedAt(BASE_TIME.plusHours(1))
                .build());

        attendanceLogRepository.save(AttendanceLog.builder()
                .activity(artsActivity).resident(alice)
                .status(AttendanceStatus.ABSENT)
                .assistanceLevel(AssistanceLevel.NONE)
                .loggedBy(staffMember)
                .loggedAt(BASE_TIME.plusDays(1).plusHours(2))
                .build());

        // Bob attended yoga, declined arts
        attendanceLogRepository.save(AttendanceLog.builder()
                .activity(yogaActivity).resident(bob)
                .status(AttendanceStatus.ATTENDED)
                .assistanceLevel(AssistanceLevel.NONE)
                .loggedBy(staffMember)
                .loggedAt(BASE_TIME.plusHours(1))
                .build());

        attendanceLogRepository.save(AttendanceLog.builder()
                .activity(artsActivity).resident(bob)
                .status(AttendanceStatus.DECLINED)
                .assistanceLevel(AssistanceLevel.NONE)
                .loggedBy(staffMember)
                .loggedAt(BASE_TIME.plusDays(1).plusHours(2))
                .build());
    }

    // -------------------------------------------------------
    // findByResidentIdOrderByLoggedAtDesc
    // -------------------------------------------------------

    @Test
    void findByResidentIdOrderByLoggedAtDesc_returnsAllLogsForResident_newestFirst() {
        List<AttendanceLog> logs = attendanceLogRepository
                .findByResidentIdOrderByLoggedAtDesc(alice.getId());

        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getLoggedAt())
                .isAfterOrEqualTo(logs.get(1).getLoggedAt());
        assertThat(logs).extracting(l -> l.getActivity().getTitle())
                .containsExactlyInAnyOrder("Morning Yoga", "Watercolour Class");
    }

    @Test
    void findByResidentIdOrderByLoggedAtDesc_returnsEmpty_forUnknownResident() {
        List<AttendanceLog> logs = attendanceLogRepository
                .findByResidentIdOrderByLoggedAtDesc(java.util.UUID.randomUUID());

        assertThat(logs).isEmpty();
    }

    // -------------------------------------------------------
    // findByResidentIdAndLoggedAtBetween
    // -------------------------------------------------------

    @Test
    void findByResidentIdAndLoggedAtBetween_returnsLogsWithinRange() {
        // loggedAt is set by @CreationTimestamp at insert time — use a window around now()
        LocalDateTime from = LocalDateTime.now().minusMinutes(5);
        LocalDateTime to   = LocalDateTime.now().plusMinutes(5);

        List<AttendanceLog> logs = attendanceLogRepository
                .findByResidentIdAndLoggedAtBetween(alice.getId(), from, to);

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(l -> l.getActivity().getTitle())
                .containsExactlyInAnyOrder("Morning Yoga", "Watercolour Class");
    }

    @Test
    void findByResidentIdAndLoggedAtBetween_returnsEmpty_whenRangeExcludesAllLogs() {
        // A range entirely in the future excludes all records
        LocalDateTime from = LocalDateTime.now().plusDays(10);
        LocalDateTime to   = LocalDateTime.now().plusDays(20);

        List<AttendanceLog> logs = attendanceLogRepository
                .findByResidentIdAndLoggedAtBetween(alice.getId(), from, to);

        assertThat(logs).isEmpty();
    }

    // -------------------------------------------------------
    // findByActivityId
    // -------------------------------------------------------

    @Test
    void findByActivityId_returnsAllLogsForActivity() {
        List<AttendanceLog> logs = attendanceLogRepository
                .findByActivityId(yogaActivity.getId());

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(l -> l.getResident().getFirstName())
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void findByActivityId_returnsEmpty_forActivityWithNoLogs() {
        Activity emptyActivity = Activity.builder()
                .title("Empty Session")
                .category(Activity.Category.SOCIAL)
                .location("Lounge")
                .startTime(BASE_TIME.plusDays(5))
                .endTime(BASE_TIME.plusDays(5).plusHours(1))
                .capacity(5)
                .status(Activity.Status.SCHEDULED)
                .build();
        entityManager.persist(emptyActivity);
        entityManager.flush();

        List<AttendanceLog> logs = attendanceLogRepository.findByActivityId(emptyActivity.getId());

        assertThat(logs).isEmpty();
    }

    // -------------------------------------------------------
    // findByActivityIdAndStatus
    // -------------------------------------------------------

    @Test
    void findByActivityIdAndStatus_returnsOnlyMatchingStatus() {
        List<AttendanceLog> attended = attendanceLogRepository
                .findByActivityIdAndStatus(yogaActivity.getId(), AttendanceStatus.ATTENDED);

        assertThat(attended).hasSize(2);
        assertThat(attended).allMatch(l -> l.getStatus() == AttendanceStatus.ATTENDED);
    }

    @Test
    void findByActivityIdAndStatus_returnsEmpty_whenNoMatchingStatus() {
        List<AttendanceLog> declined = attendanceLogRepository
                .findByActivityIdAndStatus(yogaActivity.getId(), AttendanceStatus.DECLINED);

        assertThat(declined).isEmpty();
    }

    // -------------------------------------------------------
    // countByResidentIdAndStatusAndLoggedAtAfter
    // -------------------------------------------------------

    @Test
    void countByResidentIdAndStatusAndLoggedAtAfter_countsAttendedAfterCutoff() {
        // Records are inserted with @CreationTimestamp ≈ now(); use a past cutoff to capture them
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);

        long count = attendanceLogRepository.countByResidentIdAndStatusAndLoggedAtAfter(
                alice.getId(), AttendanceStatus.ATTENDED, cutoff);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countByResidentIdAndStatusAndLoggedAtAfter_returnsZero_whenCutoffIsFuture() {
        // A future cutoff excludes all records
        LocalDateTime futureCutoff = LocalDateTime.now().plusDays(30);

        long count = attendanceLogRepository.countByResidentIdAndStatusAndLoggedAtAfter(
                alice.getId(), AttendanceStatus.ATTENDED, futureCutoff);

        assertThat(count).isZero();
    }

    // -------------------------------------------------------
    // findByActivityIdAndResidentId
    // -------------------------------------------------------

    @Test
    void findByActivityIdAndResidentId_returnsLog_whenExists() {
        Optional<AttendanceLog> log = attendanceLogRepository
                .findByActivityIdAndResidentId(yogaActivity.getId(), alice.getId());

        assertThat(log).isPresent();
        assertThat(log.get().getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    }

    @Test
    void findByActivityIdAndResidentId_returnsEmpty_whenNotLogged() {
        Optional<AttendanceLog> log = attendanceLogRepository
                .findByActivityIdAndResidentId(yogaActivity.getId(), java.util.UUID.randomUUID());

        assertThat(log).isEmpty();
    }

    // -------------------------------------------------------
    // existsByActivityIdAndResidentId
    // -------------------------------------------------------

    @Test
    void existsByActivityIdAndResidentId_returnsTrue_whenLogExists() {
        boolean exists = attendanceLogRepository
                .existsByActivityIdAndResidentId(artsActivity.getId(), bob.getId());

        assertThat(exists).isTrue();
    }

    @Test
    void existsByActivityIdAndResidentId_returnsFalse_whenLogDoesNotExist() {
        boolean exists = attendanceLogRepository
                .existsByActivityIdAndResidentId(yogaActivity.getId(), java.util.UUID.randomUUID());

        assertThat(exists).isFalse();
    }

    // -------------------------------------------------------
    // Unique constraint
    // -------------------------------------------------------

    @Test
    void save_throwsException_onDuplicateActivityResidentLog() {
        assertThrows(Exception.class, () ->
                attendanceLogRepository.saveAndFlush(AttendanceLog.builder()
                        .activity(yogaActivity)
                        .resident(alice)
                        .status(AttendanceStatus.ATTENDED)
                        .assistanceLevel(AssistanceLevel.NONE)
                        .loggedBy(staffMember)
                        .loggedAt(BASE_TIME.plusHours(2))
                        .build())
        );
    }

    // -------------------------------------------------------
    // Entity field integrity
    // -------------------------------------------------------

    @Test
    void save_defaultsAssistanceLevelToNone_whenNotSpecified() {
        Resident carol = residentRepository.save(Resident.builder()
                .firstName("Carol").lastName("White")
                .roomNumber("103").careLevel(Resident.CareLevel.HIGH)
                .isActive(true).build());

        Activity newActivity = Activity.builder()
                .title("Music Session")
                .category(Activity.Category.MUSIC)
                .location("Hall")
                .startTime(BASE_TIME.plusDays(3))
                .endTime(BASE_TIME.plusDays(3).plusHours(1))
                .capacity(15)
                .status(Activity.Status.COMPLETED)
                .build();
        entityManager.persist(newActivity);
        entityManager.flush();

        AttendanceLog saved = attendanceLogRepository.saveAndFlush(AttendanceLog.builder()
                .activity(newActivity)
                .resident(carol)
                .status(AttendanceStatus.ATTENDED)
                .loggedBy(staffMember)
                .loggedAt(BASE_TIME.plusDays(3).plusHours(1))
                .build());

        entityManager.refresh(saved);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAssistanceLevel()).isEqualTo(AssistanceLevel.NONE);
        assertThat(saved.getLoggedAt()).isNotNull();
    }
}
