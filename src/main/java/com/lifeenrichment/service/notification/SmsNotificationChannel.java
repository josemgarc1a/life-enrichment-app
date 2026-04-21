package com.lifeenrichment.service.notification;

import com.lifeenrichment.entity.NotificationLog;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Notification channel adapter for SMS delivery via the Twilio SDK.
 *
 * <p>Twilio is initialized once in {@link #init()} using the injected account SID and auth token.
 * All send errors are caught and returned as {@link ChannelResult#failure(String)}.
 */
@Slf4j
@Component
public class SmsNotificationChannel implements NotificationChannel {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    /**
     * Initializes the Twilio SDK with the configured credentials.
     * Called once by Spring after all properties have been injected.
     */
    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
        log.info("Twilio SDK initialized for account {}", accountSid);
    }

    /**
     * Returns {@code true} only when the requested channel is {@link NotificationLog.Channel#SMS}.
     *
     * <p>This adapter exclusively handles SMS delivery via Twilio; all other channel types are
     * delegated to their respective adapters by the notification service.
     *
     * @param channel the delivery channel to evaluate; never {@code null}
     * @return {@code true} if {@code channel} is {@code SMS}, {@code false} otherwise
     */
    @Override
    public boolean supports(NotificationLog.Channel channel) {
        return NotificationLog.Channel.SMS == channel;
    }

    /**
     * Sends an SMS to the recipient's phone number via Twilio.
     *
     * @param message the notification payload; {@code toAddress} must be an E.164 phone number
     * @return {@link ChannelResult#success()} on delivery; {@link ChannelResult#failure(String)} on any error
     */
    @Override
    public ChannelResult send(NotificationMessage message) {
        try {
            Message twilioMessage = Message.creator(
                    new PhoneNumber(message.getToAddress()),
                    new PhoneNumber(fromNumber),
                    message.getBody()
            ).create();

            log.info("SMS sent to {} with SID {}", message.getToAddress(), twilioMessage.getSid());
            return ChannelResult.success();
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", message.getToAddress(), e.getMessage(), e);
            return ChannelResult.failure(e.getMessage());
        }
    }
}
