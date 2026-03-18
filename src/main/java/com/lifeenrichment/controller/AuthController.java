package com.lifeenrichment.controller;

import com.lifeenrichment.dto.request.LoginRequest;
import com.lifeenrichment.dto.request.RefreshRequest;
import com.lifeenrichment.dto.request.RegisterRequest;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh, and logout")
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
}
