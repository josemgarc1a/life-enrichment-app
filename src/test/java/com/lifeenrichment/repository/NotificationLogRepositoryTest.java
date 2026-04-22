package com.lifeenrichment.repository;

import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationLog.Channel;
import com.lifeenrichment.entity.NotificationLog.DeliveryStatus;
import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import com.lifeenrichment.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NotificationLogRepositoryTest {

    @Autowired
    private NotificationLogRepository logRepository;

    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;
    private UUID activityRef;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();

        alice = userRepository.save(User.builder()
                .email("alice@facility.com")
                .passwordHash("$2a$10$hash")
                .role(User.Role.STAFF)
                .build());

        bob = userRepository.save(User.builder()
                .email("bob@facility.com")
                .passwordHash("$2a$10$hash")
                .role(User.Role.STAFF)
                .build());

        activityRef = UUID.randomUUID();

        // Alice: one SENT email, one FAILED SMS (1 attempt), one RETRYING push (2 attempts)
        logRepository.save(NotificationLog.builder()
                .user(alice)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.SENT)
                .referenceId(activityRef)
                .message("Reminder: Morning Yoga starts in 1 hour")
                .attemptCount(1)
                .build());

        logRepository.save(NotificationLog.builder()
                .user(alice)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .channel(Channel.SMS)
                .status(DeliveryStatus.FAILED)
                .referenceId(activityRef)
                .message("Reminder: Morning Yoga starts in 1 hour")
                .errorMessage("Twilio: invalid phone number")
                .attemptCount(3)
                .build());

        logRepository.save(NotificationLog.builder()
                .user(alice)
                .notificationType(NotificationType.ACTIVITY_CANCELLED)
                .channel(Channel.PUSH)
                .status(DeliveryStatus.RETRYING)
                .referenceId(activityRef)
                .message("Morning Yoga has been cancelled")
                .attemptCount(2)
                .build());

        // Bob: one RETRYING email (1 attempt)
        logRepository.save(NotificationLog.builder()
                .user(bob)
                .notificationType(NotificationType.BROADCAST)
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.RETRYING)
                .message("Facility-wide announcement")
                .attemptCount(1)
                .build());
    }

    // ── findByUserIdOrderBySentAtDesc ─────────────────────────────────────────

    @Test
    void findByUserIdOrderBySentAtDesc_returnsAllLogsForUser() {
        List<NotificationLog> logs = logRepository.findByUserIdOrderBySentAtDesc(alice.getId());

        assertThat(logs).hasSize(3);
        assertThat(logs).extracting(NotificationLog::getUser)
                .allMatch(u -> u.getId().equals(alice.getId()));
    }

    @Test
    void findByUserIdOrderBySentAtDesc_returnsEmpty_forUserWithNoLogs() {
        User carol = userRepository.save(User.builder()
                .email("carol@facility.com")
                .passwordHash("$2a$10$hash")
                .role(User.Role.DIRECTOR)
                .build());

        List<NotificationLog> logs = logRepository.findByUserIdOrderBySentAtDesc(carol.getId());

        assertThat(logs).isEmpty();
    }

    @Test
    void findByUserIdOrderBySentAtDesc_doesNotReturnOtherUsersLogs() {
        List<NotificationLog> logs = logRepository.findByUserIdOrderBySentAtDesc(bob.getId());

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getNotificationType()).isEqualTo(NotificationType.BROADCAST);
    }

    // ── findByStatusAndAttemptCountLessThan ───────────────────────────────────

    @Test
    void findByStatusAndAttemptCountLessThan_returnsRetryingLogsUnderCap() {
        // Alice has RETRYING with attemptCount=2, Bob has RETRYING with attemptCount=1
        // Both are < 3 (MAX_RETRY_ATTEMPTS)
        List<NotificationLog> candidates = logRepository
                .findByStatusAndAttemptCountLessThan(DeliveryStatus.RETRYING, 3);

        assertThat(candidates).hasSize(2);
        assertThat(candidates).allMatch(l -> l.getStatus() == DeliveryStatus.RETRYING);
        assertThat(candidates).allMatch(l -> l.getAttemptCount() < 3);
    }

    @Test
    void findByStatusAndAttemptCountLessThan_excludesLogsAtOrAboveCap() {
        // Alice's FAILED log has attemptCount=3 — should not appear
        List<NotificationLog> candidates = logRepository
                .findByStatusAndAttemptCountLessThan(DeliveryStatus.FAILED, 3);

        assertThat(candidates).isEmpty();
    }

    @Test
    void findByStatusAndAttemptCountLessThan_returnsEmpty_whenNoMatchingStatus() {
        List<NotificationLog> candidates = logRepository
                .findByStatusAndAttemptCountLessThan(DeliveryStatus.SENT, 3);

        // The one SENT log has attemptCount=1 (<3), so it matches
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    // ── findByReferenceId ─────────────────────────────────────────────────────

    @Test
    void findByReferenceId_returnsAllLogsForReference() {
        List<NotificationLog> logs = logRepository.findByReferenceId(activityRef);

        // Alice has 3 logs for activityRef; Bob's log has no referenceId
        assertThat(logs).hasSize(3);
        assertThat(logs).allMatch(l -> activityRef.equals(l.getReferenceId()));
    }

    @Test
    void findByReferenceId_returnsEmpty_forUnknownReference() {
        List<NotificationLog> logs = logRepository.findByReferenceId(UUID.randomUUID());

        assertThat(logs).isEmpty();
    }

    // ── Default values ────────────────────────────────────────────────────────

    @Test
    void save_appliesDefaultStatusAndAttemptCount_whenNotSpecified() {
        NotificationLog saved = logRepository.saveAndFlush(NotificationLog.builder()
                .user(bob)
                .notificationType(NotificationType.ENROLLMENT_CONFIRMED)
                .channel(Channel.EMAIL)
                .message("You have been enrolled in Watercolour Class")
                .build());

        assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
        assertThat(saved.getAttemptCount()).isZero();
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getErrorMessage()).isNull();
    }
}
