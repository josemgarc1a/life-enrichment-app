package com.lifeenrichment.service.notification;

import com.lifeenrichment.entity.NotificationLog;
import com.lifeenrichment.entity.NotificationPreference;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EmailNotificationChannel}.
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationChannelTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailNotificationChannel emailNotificationChannel;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailNotificationChannel, "fromAddress", "noreply@lifeenrichment.app");
    }

    // -----------------------------------------------------------------------
    // supports()
    // -----------------------------------------------------------------------

    @Test
    void supports_emailChannel_returnsTrue() {
        assertThat(emailNotificationChannel.supports(NotificationLog.Channel.EMAIL)).isTrue();
    }

    @Test
    void supports_smsChannel_returnsFalse() {
        assertThat(emailNotificationChannel.supports(NotificationLog.Channel.SMS)).isFalse();
    }

    @Test
    void supports_pushChannel_returnsFalse() {
        assertThat(emailNotificationChannel.supports(NotificationLog.Channel.PUSH)).isFalse();
    }

    // -----------------------------------------------------------------------
    // send() — happy path
    // -----------------------------------------------------------------------

    @Test
    void send_validMessage_returnsSuccessResult() throws Exception {
        // Arrange
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<html>Hello</html>");

        NotificationMessage message = NotificationMessage.builder()
                .channel(NotificationLog.Channel.EMAIL)
                .recipientUserId(UUID.randomUUID())
                .toAddress("resident@example.com")
                .subject("Activity Reminder")
                .body("Your activity starts soon.")
                .notificationType(NotificationPreference.NotificationType.ACTIVITY_REMINDER)
                .build();

        // Act
        ChannelResult result = emailNotificationChannel.send(message);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
        verify(mailSender).send(mimeMessage);
        verify(templateEngine).process(eq("notification/activity-reminder"), any(IContext.class));
    }

    // -----------------------------------------------------------------------
    // send() — template name resolution
    // -----------------------------------------------------------------------

    @Test
    void send_activityCancelledType_resolvesCorrectTemplateName() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<html>Cancelled</html>");

        NotificationMessage message = NotificationMessage.builder()
                .channel(NotificationLog.Channel.EMAIL)
                .recipientUserId(UUID.randomUUID())
                .toAddress("resident@example.com")
                .subject("Cancelled")
                .body("Your activity was cancelled.")
                .notificationType(NotificationPreference.NotificationType.ACTIVITY_CANCELLED)
                .build();

        emailNotificationChannel.send(message);

        verify(templateEngine).process(eq("notification/activity-cancelled"), any(IContext.class));
    }

    // -----------------------------------------------------------------------
    // send() — SDK exception path
    // -----------------------------------------------------------------------

    @Test
    void send_mailSenderThrowsException_returnsFailureResult() {
        // Arrange
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<html>Hello</html>");
        doThrow(new RuntimeException("SMTP connection refused")).when(mailSender).send(any(MimeMessage.class));

        NotificationMessage message = NotificationMessage.builder()
                .channel(NotificationLog.Channel.EMAIL)
                .recipientUserId(UUID.randomUUID())
                .toAddress("resident@example.com")
                .subject("Activity Reminder")
                .body("Your activity starts soon.")
                .notificationType(NotificationPreference.NotificationType.ACTIVITY_REMINDER)
                .build();

        // Act
        ChannelResult result = emailNotificationChannel.send(message);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getErrorMessage()).contains("SMTP connection refused");
    }

    @Test
    void send_templateEngineThrowsException_returnsFailureResult() {
        // Arrange — createMimeMessage is NOT stubbed because template processing fails first
        when(templateEngine.process(anyString(), any(IContext.class)))
                .thenThrow(new RuntimeException("Template not found"));

        NotificationMessage message = NotificationMessage.builder()
                .channel(NotificationLog.Channel.EMAIL)
                .recipientUserId(UUID.randomUUID())
                .toAddress("resident@example.com")
                .subject("Activity Reminder")
                .body("Body text.")
                .notificationType(NotificationPreference.NotificationType.ACTIVITY_REMINDER)
                .build();

        // Act
        ChannelResult result = emailNotificationChannel.send(message);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
    }
}
