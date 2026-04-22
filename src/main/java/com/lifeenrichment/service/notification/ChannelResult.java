package com.lifeenrichment.service.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Immutable value object representing the outcome of a single delivery attempt by a
 * {@link NotificationChannel} adapter.
 *
 * <p>Rather than throwing exceptions, every adapter wraps its result (success or failure) in a
 * {@code ChannelResult} and returns it to the notification service. The service can then
 * persist the outcome to {@code NotificationLog} without needing to handle exceptions from
 * individual channel adapters.
 *
 * <p>Prefer the static factory methods over the Lombok builder:
 * <pre>{@code
 *   // success
 *   return ChannelResult.success();
 *
 *   // failure
 *   return ChannelResult.failure(e.getMessage());
 * }</pre>
 *
 * @see NotificationChannel#send(NotificationMessage)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResult {

    /**
     * {@code true} if the downstream provider accepted the message for delivery.
     *
     * <p>Acceptance by the provider does not guarantee end-device delivery; it merely means
     * the provider received the request without returning an error.
     */
    private boolean success;

    /**
     * Human-readable description of the failure reason.
     *
     * <p>This field is {@code null} when {@link #success} is {@code true}. When present, the
     * value is typically the exception message from the downstream SDK (Twilio, FCM, JavaMail)
     * and is persisted to the {@code NotificationLog} for operator visibility.
     */
    private String errorMessage;

    /**
     * Creates a {@code ChannelResult} indicating that the downstream provider accepted the message.
     *
     * <p>The returned instance has {@code success == true} and {@code errorMessage == null}.
     *
     * @return a new successful {@link ChannelResult}
     */
    public static ChannelResult success() {
        return ChannelResult.builder()
                .success(true)
                .build();
    }

    /**
     * Creates a {@code ChannelResult} indicating that delivery failed.
     *
     * <p>The returned instance has {@code success == false} and carries the provided
     * {@code errorMessage} for logging and persistence. Callers should pass a non-null,
     * human-readable string (typically {@code exception.getMessage()}).
     *
     * @param errorMessage concise description of why delivery failed; should not be {@code null}
     * @return a new failed {@link ChannelResult} carrying the error description
     */
    public static ChannelResult failure(String errorMessage) {
        return ChannelResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
