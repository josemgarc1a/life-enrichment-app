package com.lifeenrichment.repository;

import com.lifeenrichment.entity.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ActivityRepositoryTest {

    @Autowired
    private ActivityRepository activityRepository;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 10, 0);

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();

        // Active SCHEDULED fitness activity
        activityRepository.save(Activity.builder()
                .title("Morning Yoga")
                .category(Activity.Category.FITNESS)
                .location("Garden Room")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(10)
                .status(Activity.Status.SCHEDULED)
                .build());

        // Active SCHEDULED arts activity
        activityRepository.save(Activity.builder()
                .title("Watercolour Class")
                .category(Activity.Category.ARTS)
                .location("Arts Room")
                .startTime(NOW.plusDays(2))
                .endTime(NOW.plusDays(2).plusHours(2))
                .capacity(8)
                .status(Activity.Status.SCHEDULED)
                .build());

        // Active CANCELLED social activity
        activityRepository.save(Activity.builder()
                .title("Movie Night")
                .category(Activity.Category.SOCIAL)
                .location("Common Room")
                .startTime(NOW.plusDays(3))
                .endTime(NOW.plusDays(3).plusHours(2))
                .capacity(20)
                .status(Activity.Status.CANCELLED)
                .build());

        // Soft-deleted activity — must be excluded from all queries
        activityRepository.save(Activity.builder()
                .title("Deleted Activity")
                .category(Activity.Category.FITNESS)
                .location("Gym")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(5)
                .status(Activity.Status.SCHEDULED)
                .deletedAt(NOW.minusDays(1))
                .build());
    }

    // -------------------------------------------------------
    // findByIdAndDeletedAtIsNull
    // -------------------------------------------------------

    @Test
    void findByIdAndDeletedAtIsNull_returnsActivity_whenNotDeleted() {
        Activity saved = activityRepository.save(Activity.builder()
                .title("Test Activity")
                .category(Activity.Category.COGNITIVE)
                .location("Library")
                .startTime(NOW.plusDays(5))
                .endTime(NOW.plusDays(5).plusHours(1))
                .capacity(6)
                .status(Activity.Status.SCHEDULED)
                .build());

        Optional<Activity> result = activityRepository.findByIdAndDeletedAtIsNull(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Test Activity");
    }

    @Test
    void findByIdAndDeletedAtIsNull_returnsEmpty_whenDeleted() {
        Activity deleted = activityRepository.save(Activity.builder()
                .title("To Be Deleted")
                .category(Activity.Category.SOCIAL)
                .location("Room A")
                .startTime(NOW.plusDays(1))
                .endTime(NOW.plusDays(1).plusHours(1))
                .capacity(5)
                .status(Activity.Status.SCHEDULED)
                .deletedAt(NOW.minusHours(1))
                .build());

        Optional<Activity> result = activityRepository.findByIdAndDeletedAtIsNull(deleted.getId());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------
    // findByStatusAndDeletedAtIsNull
    // -------------------------------------------------------

    @Test
    void findByStatusAndDeletedAtIsNull_returnsScheduledActivities() {
        List<Activity> result = activityRepository
                .findByStatusAndDeletedAtIsNull(Activity.Status.SCHEDULED);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(a -> a.getStatus() == Activity.Status.SCHEDULED);
    }

    @Test
    void findByStatusAndDeletedAtIsNull_returnsCancelledActivities() {
        List<Activity> result = activityRepository
                .findByStatusAndDeletedAtIsNull(Activity.Status.CANCELLED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Movie Night");
    }

    @Test
    void findByStatusAndDeletedAtIsNull_excludesDeletedActivities() {
        List<Activity> scheduled = activityRepository
                .findByStatusAndDeletedAtIsNull(Activity.Status.SCHEDULED);

        assertThat(scheduled).noneMatch(a -> a.getTitle().equals("Deleted Activity"));
    }

    // -------------------------------------------------------
    // findByStartTimeBetweenAndDeletedAtIsNull
    // -------------------------------------------------------

    @Test
    void findByStartTimeBetweenAndDeletedAtIsNull_returnsActivitiesInRange() {
        List<Activity> result = activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(
                NOW, NOW.plusDays(2).plusHours(1));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Activity::getTitle)
                .containsExactlyInAnyOrder("Morning Yoga", "Watercolour Class");
    }

    @Test
    void findByStartTimeBetweenAndDeletedAtIsNull_excludesActivitiesOutsideRange() {
        List<Activity> result = activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(
                NOW.plusDays(10), NOW.plusDays(20));

        assertThat(result).isEmpty();
    }

    @Test
    void findByStartTimeBetweenAndDeletedAtIsNull_excludesDeletedActivities() {
        // The deleted "Deleted Activity" starts at NOW+1d — same range as "Morning Yoga"
        List<Activity> result = activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(
                NOW, NOW.plusDays(2));

        assertThat(result).noneMatch(a -> a.getTitle().equals("Deleted Activity"));
    }

    // -------------------------------------------------------
    // findByCategoryAndDeletedAtIsNull
    // -------------------------------------------------------

    @Test
    void findByCategoryAndDeletedAtIsNull_returnsActivitiesInCategory() {
        List<Activity> result = activityRepository
                .findByCategoryAndDeletedAtIsNull(Activity.Category.FITNESS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Morning Yoga");
    }

    @Test
    void findByCategoryAndDeletedAtIsNull_returnsEmpty_whenNoneMatch() {
        List<Activity> result = activityRepository
                .findByCategoryAndDeletedAtIsNull(Activity.Category.MUSIC);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------
    // Pageable listing methods
    // -------------------------------------------------------

    @Test
    void findByDeletedAtIsNull_returnsAllNonDeletedActivities() {
        Page<Activity> page = activityRepository.findByDeletedAtIsNull(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findByDeletedAtIsNullAndStatus_filtersCorrectly() {
        Page<Activity> page = activityRepository.findByDeletedAtIsNullAndStatus(
                Activity.Status.SCHEDULED, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByDeletedAtIsNullAndCategory_filtersCorrectly() {
        Page<Activity> page = activityRepository.findByDeletedAtIsNullAndCategory(
                Activity.Category.ARTS, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Watercolour Class");
    }

    @Test
    void findByDeletedAtIsNullAndCategoryAndStatus_filtersCorrectly() {
        Page<Activity> page = activityRepository.findByDeletedAtIsNullAndCategoryAndStatus(
                Activity.Category.SOCIAL, Activity.Status.CANCELLED, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Movie Night");
    }

    // -------------------------------------------------------
    // Entity field integrity
    // -------------------------------------------------------

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void save_persistsAllFields_andDefaultsStatusToScheduled() {
        Activity saved = activityRepository.saveAndFlush(Activity.builder()
                .title("Brain Quiz")
                .category(Activity.Category.COGNITIVE)
                .location("Library")
                .startTime(NOW.plusDays(7))
                .endTime(NOW.plusDays(7).plusHours(1))
                .capacity(12)
                .recurrenceRule("FREQ=WEEKLY;BYDAY=FR")
                .build());

        entityManager.refresh(saved);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(Activity.Status.SCHEDULED);
        assertThat(saved.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY;BYDAY=FR");
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
