package com.lifeenrichment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task execution capability.
 *
 * <p>Required for {@code @Scheduled} methods (e.g. in {@code NotificationScheduler})
 * to be registered and executed on the Spring task-scheduler thread pool.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
