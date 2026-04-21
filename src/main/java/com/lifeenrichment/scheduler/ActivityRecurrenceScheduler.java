package com.lifeenrichment.scheduler;

import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.repository.ActivityRepository;
import com.lifeenrichment.service.ActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Nightly job that keeps the recurring-activity calendar window filled 8 weeks ahead.
 *
 * <p>For every active series template (an {@link Activity} with a non-null
 * {@code recurrenceRule}), the scheduler delegates to
 * {@link ActivityService#expandSeries} which is idempotent — it only creates
 * occurrence rows that do not yet exist for the target start time.
 *
 * <p>Runs at 02:00 AM server time every day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityRecurrenceScheduler {

    private final ActivityRepository activityRepository;
    private final ActivityService activityService;

    /**
     * Fills the 8-week rolling window for all active recurring activity series.
     * Safe to call multiple times — {@code expandSeries} skips already-existing occurrences.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void fillRecurringWindow() {
        List<Activity> templates = activityRepository
                .findByRecurrenceRuleIsNotNullAndDeletedAtIsNullAndStatus(Activity.Status.SCHEDULED);

        log.info("Recurring window fill started — {} active series found", templates.size());

        int totalNew = 0;
        for (Activity template : templates) {
            try {
                List<Activity> created = activityService.expandSeries(
                        template, ActivityService.RECURRENCE_WINDOW_WEEKS);
                totalNew += created.size();
                if (!created.isEmpty()) {
                    log.debug("Series {}: generated {} new occurrences", template.getId(), created.size());
                }
            } catch (Exception e) {
                log.error("Failed to expand series {} ({}): {}", template.getId(), template.getTitle(), e.getMessage(), e);
            }
        }

        log.info("Recurring window fill complete — {} new occurrences generated across {} series",
                totalNew, templates.size());
    }
}
