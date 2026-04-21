package com.lifeenrichment.service.notification;

import com.lifeenrichment.entity.NotificationLog;

/**
 * Strategy interface for notification channel adapters.
 *
 * <p>Each implementation handles delivery over exactly one channel (EMAIL, SMS, or PUSH).
 * At runtime the notification service iterates over all Spring-managed
 * {@code NotificationChannel} beans, calls {@link #supports(NotificationLog.Channel)}
 * to find the matching adapter, and then delegates to {@link #send(NotificationMessage)}.
 * Adding a new delivery channel requires only a new {@code @Component} that implements
 * this interface — no changes to the service layer are needed.
 *
 * <p><strong>Contract for all implementations:</strong>
 * <ul>
 *   <li>{@link #supports} must be pure (no side effects, no I/O).</li>
 *   <li>{@link #send} must never propagate exceptions to the caller; every error
 *       must be caught internally and returned as {@link ChannelResult#failure(String)}.</li>
 *   <li>Exactly one registered adapter should return {@code true} for any given channel.</li>
 * </ul>
 *
 * @see EmailNotificationChannel
 * @see SmsNotificationChannel
 * @see PushNotificationChannel
 * @see ChannelResult
 * @see NotificationMessage
 */
public interface NotificationChannel {

    /**
     * Determines whether this adapter is responsible for the given delivery channel.
     *
     * <p>The notification service uses this method as a discriminator: it iterates all
     * registered {@code NotificationChannel} beans and selects the first one that returns
     * {@code true}. Implementations should perform an equality check against the single
     * channel they own (e.g. {@code NotificationLog.Channel.EMAIL == channel}) and must
     * not perform any I/O or state mutation.
     *
     * @param channel the delivery channel to check; never {@code null}
     * @return {@code true} when this adapter owns the given channel, {@code false} otherwise
     */
    boolean supports(NotificationLog.Channel channel);

    /**
     * Attempts to deliver the notification described by {@code message} over this adapter's channel.
     *
     * <p><strong>Implementor contract:</strong>
     * <ul>
     *   <li>This method must never throw a checked or unchecked exception.
     *       All exceptions from the downstream provider must be caught and converted
     *       to {@link ChannelResult#failure(String)} so that the service layer can
     *       persist the outcome and continue processing other recipients.</li>
     *   <li>A {@link ChannelResult#success()} return value means the message was accepted
     *       by the provider — it does <em>not</em> guarantee delivery to the end device.</li>
     *   <li>Implementations should log failures at {@code ERROR} level before returning
     *       a failure result so that problems are visible in the application logs.</li>
     * </ul>
     *
     * @param message the notification payload containing recipient address, subject, body,
     *                and metadata required by this channel; never {@code null}
     * @return a non-null {@link ChannelResult} indicating whether the provider accepted the message
     */
    ChannelResult send(NotificationMessage message);
}
