package com.lifeenrichment.repository;

import com.lifeenrichment.entity.NotificationPreference;
import com.lifeenrichment.entity.NotificationPreference.NotificationType;
import com.lifeenrichment.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class NotificationPreferenceRepositoryTest {

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        preferenceRepository.deleteAll();

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

        // Alice has two preferences configured
        preferenceRepository.save(NotificationPreference.builder()
                .user(alice)
                .notificationType(NotificationType.ACTIVITY_REMINDER)
                .emailEnabled(true)
                .smsEnabled(true)
                .pushEnabled(false)
                .build());

        preferenceRepository.save(NotificationPreference.builder()
                .user(alice)
                .notificationType(NotificationType.ACTIVITY_CANCELLED)
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(false)
                .build());

        // Bob has one preference configured
        preferenceRepository.save(NotificationPreference.builder()
                .user(bob)
                .notificationType(NotificationType.BROADCAST)
                .emailEnabled(false)
                .smsEnabled(false)
                .pushEnabled(true)
                .build());
    }

    // ── findByUserId ──────────────────────────────────────────────────────────

    @Test
    void findByUserId_returnsAllPreferencesForUser() {
        List<NotificationPreference> prefs = preferenceRepository.findByUserId(alice.getId());

        assertThat(prefs).hasSize(2);
        assertThat(prefs).extracting(NotificationPreference::getNotificationType)
                .containsExactlyInAnyOrder(NotificationType.ACTIVITY_REMINDER, NotificationType.ACTIVITY_CANCELLED);
    }

    @Test
    void findByUserId_returnsEmpty_whenUserHasNoPreferences() {
        User carol = userRepository.save(User.builder()
                .email("carol@facility.com")
                .passwordHash("$2a$10$hash")
                .role(User.Role.DIRECTOR)
                .build());

        List<NotificationPreference> prefs = preferenceRepository.findByUserId(carol.getId());

        assertThat(prefs).isEmpty();
    }

    @Test
    void findByUserId_doesNotReturnOtherUsersPreferences() {
        List<NotificationPreference> prefs = preferenceRepository.findByUserId(bob.getId());

        assertThat(prefs).hasSize(1);
        assertThat(prefs.get(0).getNotificationType()).isEqualTo(NotificationType.BROADCAST);
    }

    // ── findByUserIdAndNotificationType ──────────────────────────────────────

    @Test
    void findByUserIdAndNotificationType_returnsPreference_whenExists() {
        Optional<NotificationPreference> pref = preferenceRepository
                .findByUserIdAndNotificationType(alice.getId(), NotificationType.ACTIVITY_REMINDER);

        assertThat(pref).isPresent();
        assertThat(pref.get().isSmsEnabled()).isTrue();
        assertThat(pref.get().isEmailEnabled()).isTrue();
        assertThat(pref.get().isPushEnabled()).isFalse();
    }

    @Test
    void findByUserIdAndNotificationType_returnsEmpty_whenTypeNotConfigured() {
        Optional<NotificationPreference> pref = preferenceRepository
                .findByUserIdAndNotificationType(alice.getId(), NotificationType.ENROLLMENT_CONFIRMED);

        assertThat(pref).isEmpty();
    }

    @Test
    void findByUserIdAndNotificationType_returnsEmpty_forUnknownUser() {
        Optional<NotificationPreference> pref = preferenceRepository
                .findByUserIdAndNotificationType(java.util.UUID.randomUUID(), NotificationType.ACTIVITY_REMINDER);

        assertThat(pref).isEmpty();
    }

    // ── Default values ────────────────────────────────────────────────────────

    @Test
    void save_appliesDefaultChannelFlags_whenNotSpecified() {
        NotificationPreference saved = preferenceRepository.saveAndFlush(
                NotificationPreference.builder()
                        .user(bob)
                        .notificationType(NotificationType.ENROLLMENT_CONFIRMED)
                        .build());

        assertThat(saved.isEmailEnabled()).isTrue();
        assertThat(saved.isSmsEnabled()).isFalse();
        assertThat(saved.isPushEnabled()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    // ── Unique constraint ─────────────────────────────────────────────────────

    @Test
    void save_throwsException_onDuplicateUserAndType() {
        assertThrows(Exception.class, () ->
                preferenceRepository.saveAndFlush(NotificationPreference.builder()
                        .user(alice)
                        .notificationType(NotificationType.ACTIVITY_REMINDER)
                        .emailEnabled(false)
                        .build())
        );
    }
}
