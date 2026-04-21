package com.lifeenrichment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's asynchronous method execution capability.
 *
 * <p>Required for {@code @Async} methods in {@code NotificationService} to execute
 * on a separate thread pool rather than the caller's thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
