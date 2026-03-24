package com.lifeenrichment.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Response payload returned on successful authentication (register, login, refresh).
 *
 * <p>The {@code accessToken} should be included in every subsequent request as
 * {@code Authorization: Bearer <accessToken>}. The {@code refreshToken} is used only
 * to obtain a new access token when the current one expires.
 */
@Getter
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String role;
}
