package com.lifeenrichment.service.notification;

import com.lifeenrichment.entity.NotificationLog;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Notification channel adapter for email delivery via JavaMailSender and Thymeleaf templates.
 *
 * <p>Selects the Thymeleaf template based on the notification type. Template names are derived
 * by lower-casing the {@code NotificationType} enum name and replacing underscores with hyphens,
 * e.g. {@code ACTIVITY_REMINDER} → {@code notification/activity-reminder}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:noreply@lifeenrichment.app}")
    private String fromAddress;

    /**
     * Returns {@code true} only when the requested channel is {@link NotificationLog.Channel#EMAIL}.
     *
     * <p>This adapter exclusively handles email delivery; all other channel types are
     * delegated to their respective adapters by the notification service.
     *
     * @param channel the delivery channel to evaluate; never {@code null}
     * @return {@code true} if {@code channel} is {@code EMAIL}, {@code false} otherwise
     */
    @Override
    public boolean supports(NotificationLog.Channel channel) {
        return NotificationLog.Channel.EMAIL == channel;
    }

    /**
     * Sends an HTML email using the Thymeleaf template resolved from the notification type.
     *
     * @param message the notification payload
     * @return {@link ChannelResult#success()} on delivery; {@link ChannelResult#failure(String)} on any error
     */
    @Override
    public ChannelResult send(NotificationMessage message) {
        try {
            String templateName = resolveTemplateName(message);
            Context context = buildThymeleafContext(message);
            String htmlBody = templateEngine.process(templateName, context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(message.getToAddress());
            helper.setSubject(message.getSubject());
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("Email sent to {} via template {}", message.getToAddress(), templateName);
            return ChannelResult.success();
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", message.getToAddress(), e.getMessage(), e);
            return ChannelResult.failure(e.getMessage());
        }
    }

    /**
     * Converts the notification type enum name to a Thymeleaf template path.
     * Example: {@code ACTIVITY_REMINDER} → {@code notification/activity-reminder}.
     */
    private String resolveTemplateName(NotificationMessage message) {
        String typeName = message.getNotificationType().name()
                .toLowerCase()
                .replace('_', '-');
        return "notification/" + typeName;
    }

    /**
     * Populates a Thymeleaf {@link Context} with variables available to all notification templates.
     */
    private Context buildThymeleafContext(NotificationMessage message) {
        Context context = new Context();
        context.setVariable("body", message.getBody());
        context.setVariable("subject", message.getSubject());
        context.setVariable("message", message.getBody());
        return context;
    }
}
