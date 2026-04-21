package com.lifeenrichment.service.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Notification;
import com.lifeenrichment.entity.NotificationLog;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Notification channel adapter for push delivery via Firebase Cloud Messaging (FCM).
 *
 * <p>The Firebase Admin SDK is initialized once in {@link #init()} using the service-account
 * JSON file located at {@code firebase.credentials-path}. A guard against duplicate
 * initialization is included so the method is safe to call in any context.
 */
@Slf4j
@Component
public class PushNotificationChannel implements NotificationChannel {

    @Value("${firebase.credentials-path}")
    private String credentialsPath;

    /**
     * Initializes the Firebase Admin SDK if it has not been initialized yet.
     * Reads the service-account JSON from the path configured in {@code firebase.credentials-path}.
     */
    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = new FileInputStream(credentialsPath);
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized from {}", credentialsPath);
            } else {
                log.debug("Firebase Admin SDK already initialized; skipping re-init.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage(), e);
        }
    }

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

            String messageId = FirebaseMessaging.getInstance().send(fcmMessage);
            log.info("Push notification sent to token {} with message ID {}", message.getToAddress(), messageId);
            return ChannelResult.success();
        } catch (Exception e) {
            log.error("Failed to send push notification to {}: {}", message.getToAddress(), e.getMessage(), e);
            return ChannelResult.failure(e.getMessage());
        }
    }
}
