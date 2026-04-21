package com.lifeenrichment.service.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Value object representing the outcome of a single channel delivery attempt.
 *
 * <p>Use the static factory methods {@link #success()} and {@link #failure(String)} rather
 * than the builder directly.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResult {

    /** {@code true} if the message was accepted by the downstream provider. */
    private boolean success;

    /** Human-readable error description; {@code null} when {@link #success} is {@code true}. */
    private String errorMessage;

    /**
     * Returns a successful result with no error message.
     *
     * @return a {@link ChannelResult} indicating delivery success
     */
    public static ChannelResult success() {
        return ChannelResult.builder()
                .success(true)
                .build();
    }

    /**
     * Returns a failed result with the supplied error message.
     *
     * @param errorMessage description of the failure; should not be {@code null}
     * @return a {@link ChannelResult} indicating delivery failure
     */
    public static ChannelResult failure(String errorMessage) {
        return ChannelResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
