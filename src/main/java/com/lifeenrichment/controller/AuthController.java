package com.lifeenrichment.controller;

import com.lifeenrichment.dto.request.ForgotPasswordRequest;
import com.lifeenrichment.dto.request.LoginRequest;
import com.lifeenrichment.dto.request.RefreshRequest;
import com.lifeenrichment.dto.request.RegisterRequest;
import com.lifeenrichment.dto.request.ResetPasswordRequest;
import com.lifeenrichment.dto.response.AuthResponse;
import com.lifeenrichment.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for all authentication flows: registration, login, token refresh,
 * logout, and the two-step password-reset process.
 *
 * <p>All endpoints under {@code /api/v1/auth} are publicly accessible (no JWT required).
 * Business logic is fully delegated to {@link com.lifeenrichment.service.AuthService}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh, logout, and password reset")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user",
               description = "Creates a new account for a Director, Staff, or Family Member and returns JWT tokens.")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Validation error or email already registered")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Login",
               description = "Authenticates with email + password and returns a signed access token and refresh token.")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Refresh access token",
               description = "Exchanges a valid refresh token for a new access token without re-authenticating.")
    @ApiResponse(responseCode = "200", description = "New access token issued")
    @ApiResponse(responseCode = "400", description = "Invalid or expired refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @Operation(summary = "Logout",
               description = "Invalidates the refresh token server-side so it cannot be used again.")
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request password reset",
               description = "Sends a password reset email if the address is registered. Always returns 202 to prevent user enumeration.")
    @ApiResponse(responseCode = "202", description = "Reset email sent if address exists")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Reset password",
               description = "Sets a new password using the secure token received by email. Token is single-use and expires in 1 hour.")
    @ApiResponse(responseCode = "204", description = "Password updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-used token")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
