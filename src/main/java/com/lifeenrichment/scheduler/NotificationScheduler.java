package com.lifeenrichment.scheduler;

import com.lifeenrichment.entity.Activity;
import com.lifeenrichment.repository.ActivityRepository;
import com.lifeenrichment.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduled jobs responsible for dispatching activity reminder notifications
 * and retrying previously failed notification deliveries.
 *
 * <p>Two independent cron jobs are defined:
 * <ul>
 *   <li>{@link #sendReminders()} — runs every 15 minutes, queries activities whose
 *       {@code startTime} falls within the configurable lead-time window, and dispatches
 *       a reminder for each {@code SCHEDULED} activity found.</li>
 *   <li>{@link #retryFailedNotifications()} — runs every 30 minutes and delegates to
 *       {@link NotificationService#retryFailed()} to re-attempt any {@code RETRYING}
 *       notification log entries.</li>
 * </ul>
 *
 * <p>Each job is individually wrapped in a try/catch block so that a failure in one
 * job does not prevent the other from running on its next scheduled trigger.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;
    private final ActivityRepository activityRepository;

    /**
     * Number of minutes before an activity's start time at which the reminder is sent.
     * Defaults to 60; override via {@code app.notifications.reminder-lead-minutes}.
     */
    @Value("${app.notifications.reminder-lead-minutes:60}")
    private int reminderLeadMinutes;

    // -------------------------------------------------------------------------
    // Reminder job — every 15 minutes
    // -------------------------------------------------------------------------

    /**
     * Queries all non-deleted activities whose {@code startTime} falls within the
     * upcoming reminder window (from {@code now + reminderLeadMinutes} to
     * {@code now + reminderLeadMinutes + 15 minutes}), filters for {@code SCHEDULED}
     * status in-memory, and dispatches an activity reminder for each match.
     *
     * <p>Runs on the cron expression {@code 0 0/15 * * * *} (every 15 minutes on the
     * hour boundary).
     */
    @Scheduled(cron = "0 0/15 * * * *")
    public void sendReminders() {
        log.info("sendReminders: starting activity reminder job (reminderLeadMinutes={})", reminderLeadMinutes);
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.plusMinutes(reminderLeadMinutes);
            LocalDateTime windowEnd   = windowStart.plusMinutes(15);

            List<Activity> candidates =
                    activityRepository.findByStartTimeBetweenAndDeletedAtIsNull(windowStart, windowEnd);

            List<Activity> scheduled = candidates.stream()
                    .filter(a -> Activity.Status.SCHEDULED.equals(a.getStatus()))
                    .collect(Collectors.toList());

            log.info("sendReminders: found {} SCHEDULED activity/activities in window [{}, {}]",
                    scheduled.size(), windowStart, windowEnd);

            for (Activity activity : scheduled) {
                notificationService.sendActivityReminder(activity.getId(), null);
            }

            log.info("sendReminders: dispatched reminders for {} activity/activities", scheduled.size());
        } catch (Exception e) {
            log.error("sendReminders: unexpected error during reminder job", e);
        }
    }

    // -------------------------------------------------------------------------
    // Retry job — every 30 minutes
    // -------------------------------------------------------------------------

    /**
     * Delegates to {@link NotificationService#retryFailed()} to re-attempt any
     * notification log entries that are in {@code RETRYING} status and have not yet
     * exhausted the maximum attempt count.
     *
     * <p>Runs on the cron expression {@code 0 0/30 * * * *} (every 30 minutes on the
     * hour boundary).
     */
    @Scheduled(cron = "0 0/30 * * * *")
    public void retryFailedNotifications() {
        log.info("retryFailedNotifications: starting retry job");
        try {
            notificationService.retryFailed();
            log.info("retryFailedNotifications: retry job completed");
        } catch (Exception e) {
            log.error("retryFailedNotifications: unexpected error during retry job", e);
        }
    }
}
