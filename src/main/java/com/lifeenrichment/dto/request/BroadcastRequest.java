package com.lifeenrichment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request body for a Director-initiated broadcast notification.
 *
 * <p>{@code message} is mandatory and must not be blank. {@code targetUserIds} is optional;
 * when null or empty the controller resolves all users in the system and passes them to
 * {@code NotificationService#sendBroadcast}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastRequest {

    /** The message body to broadcast. Must not be blank. */
    @NotBlank
    private String message;

    /**
     * Optional list of target user IDs. When null or empty the broadcast is sent to all users
     * currently registered in the system.
     */
    private List<UUID> targetUserIds;
}
