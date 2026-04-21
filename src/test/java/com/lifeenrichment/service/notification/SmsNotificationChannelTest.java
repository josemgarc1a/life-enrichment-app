package com.lifeenrichment.service.notification;

import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationPreference;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SmsNotificationChannel}.
 *
 * <p>Twilio's {@link Message#creator} is a static factory; tests use Mockito's
 * {@code mockStatic} to intercept it without touching real Twilio credentials.
 */
@ExtendWith(MockitoExtension.class)
class SmsNotificationChannelTest {

    private SmsNotificationChannel smsNotificationChannel;

    @BeforeEach
    void setUp() {
        smsNotificationChannel = new SmsNotificationChannel();
        ReflectionTestUtils.setField(smsNotificationChannel, "accountSid", "ACTEST00000000000000000000000000000");
        ReflectionTestUtils.setField(smsNotificationChannel, "authToken", "test_auth_token");
        ReflectionTestUtils.setField(smsNotificationChannel, "fromNumber", "+15550000000");
        // Do NOT call smsNotificationChannel.init() — that would invoke Twilio.init() for real.
    }

    // -----------------------------------------------------------------------
    // supports()
    // -----------------------------------------------------------------------

    @Test
    void supports_smsChannel_returnsTrue() {
        assertThat(smsNotificationChannel.supports(NotificationLog.Channel.SMS)).isTrue();
    }

    @Test
    void supports_emailChannel_returnsFalse() {
        assertThat(smsNotificationChannel.supports(NotificationLog.Channel.EMAIL)).isFalse();
    }

    @Test
    void supports_pushChannel_returnsFalse() {
        assertThat(smsNotificationChannel.supports(NotificationLog.Channel.PUSH)).isFalse();
    }

    // -----------------------------------------------------------------------
    // send() — happy path
    // -----------------------------------------------------------------------

    @Test
    void send_validMessage_returnsSuccessResult() {
        NotificationMessage message = buildMessage("+15551234567", "You have an activity soon.");

        MessageCreator mockCreator = mock(MessageCreator.class);
        Message mockTwilioMessage = mock(Message.class);
        when(mockTwilioMessage.getSid()).thenReturn("SM_TEST_SID");
        when(mockCreator.create()).thenReturn(mockTwilioMessage);

        try (MockedStatic<Twilio> twilioStatic = mockStatic(Twilio.class);
             MockedStatic<Message> messageStatic = mockStatic(Message.class)) {

            // Suppress Twilio.init in case it's triggered
            twilioStatic.when(() -> Twilio.init(anyString(), anyString())).thenAnswer(inv -> null);

            messageStatic.when(() -> Message.creator(
                    any(PhoneNumber.class),
                    any(PhoneNumber.class),
                    anyString()
            )).thenReturn(mockCreator);

            ChannelResult result = smsNotificationChannel.send(message);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getErrorMessage()).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // send() — SDK exception path
    // -----------------------------------------------------------------------

    @Test
    void send_twilioCreatorThrowsException_returnsFailureResult() {
        NotificationMessage message = buildMessage("+15551234567", "You have an activity soon.");

        MessageCreator mockCreator = mock(MessageCreator.class);
        when(mockCreator.create()).thenThrow(new RuntimeException("Twilio API error: invalid credentials"));

        try (MockedStatic<Twilio> twilioStatic = mockStatic(Twilio.class);
             MockedStatic<Message> messageStatic = mockStatic(Message.class)) {

            twilioStatic.when(() -> Twilio.init(anyString(), anyString())).thenAnswer(inv -> null);

            messageStatic.when(() -> Message.creator(
                    any(PhoneNumber.class),
                    any(PhoneNumber.class),
                    anyString()
            )).thenReturn(mockCreator);

            ChannelResult result = smsNotificationChannel.send(message);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isNotNull();
            assertThat(result.getErrorMessage()).contains("Twilio API error");
        }
    }

    @Test
    void send_messageCreatorStaticThrowsException_returnsFailureResult() {
        NotificationMessage message = buildMessage("+15551234567", "You have an activity soon.");

        try (MockedStatic<Twilio> twilioStatic = mockStatic(Twilio.class);
             MockedStatic<Message> messageStatic = mockStatic(Message.class)) {

            twilioStatic.when(() -> Twilio.init(anyString(), anyString())).thenAnswer(inv -> null);

            messageStatic.when(() -> Message.creator(
                    any(PhoneNumber.class),
                    any(PhoneNumber.class),
                    anyString()
            )).thenThrow(new RuntimeException("Unable to create message"));

            ChannelResult result = smsNotificationChannel.send(message);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private NotificationMessage buildMessage(String toPhone, String body) {
        return NotificationMessage.builder()
                .channel(NotificationLog.Channel.SMS)
                .recipientUserId(UUID.randomUUID())
                .toAddress(toPhone)
                .subject("Activity Reminder")
                .body(body)
                .notificationType(NotificationPreference.NotificationType.ACTIVITY_REMINDER)
                .build();
    }
}
