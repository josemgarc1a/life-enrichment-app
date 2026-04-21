package com.lifeenrichment.service.notification;

import com.lifeenrichment.entity.NotificationLog;

/**
 * Strategy interface for notification channel adapters.
 *
 * <p>Each implementation handles delivery over a single channel (EMAIL, SMS, PUSH).
 * The service layer calls {@link #supports(NotificationLog.Channel)} to select the
 * correct adapter at runtime, then delegates to {@link #send(NotificationMessage)}.
 */
public interface NotificationChannel {

    /**
     * Returns {@code true} if this adapter handles the given channel.
     *
     * @param channel the delivery channel to check
     * @return {@code true} when this adapter supports the channel
     */
    boolean supports(NotificationLog.Channel channel);

    /**
     * Sends the notification described by {@code message}.
     *
     * <p>Implementations must never throw — all exceptions must be caught and
     * returned as {@link ChannelResult#failure(String)}.
     *
     * @param message the notification payload
     * @return a {@link ChannelResult} indicating success or failure
     */
    ChannelResult send(NotificationMessage message);
}
