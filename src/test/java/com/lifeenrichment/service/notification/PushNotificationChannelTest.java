package com.lifeenrichment.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationPreference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PushNotificationChannel}.
 *
 * <p>{@link FirebaseMessaging} is injected via constructor so it can be mocked without
 * touching real Firebase credentials or static SDK state.
 */
@ExtendWith(MockitoExtension.class)
class PushNotificationChannelTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private PushNotificationChannel pushNotificationChannel;

    // -----------------------------------------------------------------------
    // supports()
    // -----------------------------------------------------------------------

    @Test
    void supports_pushChannel_returnsTrue() {
        assertThat(pushNotificationChannel.supports(NotificationLog.Channel.PUSH)).isTrue();
    }

    @Test
    void supports_emailChannel_returnsFalse() {
        assertThat(pushNotificationChannel.supports(NotificationLog.Channel.EMAIL)).isFalse();
    }

    @Test
    void supports_smsChannel_returnsFalse() {
        assertThat(pushNotificationChannel.supports(NotificationLog.Channel.SMS)).isFalse();
    }

    // -----------------------------------------------------------------------
    // send() — happy path
    // -----------------------------------------------------------------------

    @Test
    void send_validMessage_returnsSuccessResult() throws Exception {
        when(firebaseMessaging.send(any(com.google.firebase.messaging.Message.class)))
                .thenReturn("projects/test-project/messages/0:1234567890");

        NotificationMessage message = buildMessage("device-token-abc123", "Activity starts in 30 minutes.");

        ChannelResult result = pushNotificationChannel.send(message);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
    }

    // -----------------------------------------------------------------------
    // send() — SDK exception path
    // -----------------------------------------------------------------------

    @Test
    void send_firebaseMessagingThrowsException_returnsFailureResult() throws Exception {
        FirebaseMessagingException fcmException = mock(FirebaseMessagingException.class);
        when(fcmException.getMessage()).thenReturn("FCM: invalid registration token");
        when(firebaseMessaging.send(any(com.google.firebase.messaging.Message.class)))
                .thenThrow(fcmException);

        NotificationMessage message = buildMessage("invalid-token", "Activity starts in 30 minutes.");

        ChannelResult result = pushNotificationChannel.send(message);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getErrorMessage()).contains("FCM");
    }

    @Test
    void send_runtimeExceptionFromFirebase_returnsFailureResult() throws Exception {
        when(firebaseMessaging.send(any(com.google.firebase.messaging.Message.class)))
                .thenThrow(new RuntimeException("Network timeout"));

        NotificationMessage message = buildMessage("device-token-xyz", "Body text.");

        ChannelResult result = pushNotificationChannel.send(message);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getErrorMessage()).contains("Network timeout");
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private NotificationMessage buildMessage(String deviceToken, String body) {
        return NotificationMessage.builder()
                .channel(NotificationLog.Channel.PUSH)
                .recipientUserId(UUID.randomUUID())
                .toAddress(deviceToken)
                .subject("Activity Reminder")
                .body(body)
                .notificationType(NotificationPreference.NotificationType.ACTIVITY_REMINDER)
                .build();
    }

    private static FirebaseMessagingException mock(Class<FirebaseMessagingException> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}
