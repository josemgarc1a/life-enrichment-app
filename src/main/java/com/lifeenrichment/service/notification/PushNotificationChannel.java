package com.lifeenrichment.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Notification;
import com.lifeenrichment.entity.NotificationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Notification channel adapter for push delivery via Firebase Cloud Messaging (FCM).
 *
 * <p>Receives a {@link FirebaseMessaging} instance via constructor injection (provided by
 * {@code FirebaseConfig}). All send errors are caught and returned as
 * {@link ChannelResult#failure(String)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationChannel implements NotificationChannel {

    private final FirebaseMessaging firebaseMessaging;

    /**
     * Returns {@code true} only when the requested channel is {@link NotificationLog.Channel#PUSH}.
     *
     * <p>This adapter exclusively handles push notification delivery via FCM; all other channel
     * types are delegated to their respective adapters by the notification service.
     *
     * @param channel the delivery channel to evaluate; never {@code null}
     * @return {@code true} if {@code channel} is {@code PUSH}, {@code false} otherwise
     */
    @Override
    public boolean supports(NotificationLog.Channel channel) {
        return NotificationLog.Channel.PUSH == channel;
    }

    /**
     * Sends a push notification to the device token stored in {@code message.getToAddress()}.
     *
     * @param message the notification payload; {@code toAddress} must be an FCM device registration token
     * @return {@link ChannelResult#success()} on delivery; {@link ChannelResult#failure(String)} on any error
     */
    @Override
    public ChannelResult send(NotificationMessage message) {
        try {
            com.google.firebase.messaging.Message fcmMessage =
                    com.google.firebase.messaging.Message.builder()
                            .setToken(message.getToAddress())
                            .setNotification(
                                    Notification.builder()
                                            .setTitle(message.getSubject())
                                            .setBody(message.getBody())
                                            .build()
                            )
                            .build();

            String messageId = firebaseMessaging.send(fcmMessage);
            log.info("Push notification sent to token {} with message ID {}", message.getToAddress(), messageId);
            return ChannelResult.success();
        } catch (Exception e) {
            log.error("Failed to send push notification to {}: {}", message.getToAddress(), e.getMessage(), e);
            return ChannelResult.failure(e.getMessage());
        }
    }
}
