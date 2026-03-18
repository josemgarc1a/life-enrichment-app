package com.lifeenrichment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeenrichment.dto.request.LoginRequest;
import com.lifeenrichment.dto.request.RefreshRequest;
import com.lifeenrichment.dto.request.RegisterRequest;
import com.lifeenrichment.dto.response.AuthResponse;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.security.jwt.JwtUtils;
import com.lifeenrichment.service.AuthService;
import com.lifeenrichment.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtUtils jwtUtils;                      // satisfies JwtAuthFilter dependency
    @MockBean UserDetailsService userDetailsService;  // satisfies JwtAuthFilter dependency

    private static final AuthResponse SAMPLE_RESPONSE = AuthResponse.builder()
            .accessToken("access.token.here")
            .refreshToken("refresh.token.here")
            .expiresIn(86400000L)
            .role("DIRECTOR")
            .build();

    // ── POST /register ────────────────────────────────────────────────────────

    @Test
    void register_returns201_onValidRequest() throws Exception {
        when(authService.register(any())).thenReturn(SAMPLE_RESPONSE);

        RegisterRequest body = RegisterRequest.builder()
                .email("director@example.com")
                .password("securePass1")
                .role(User.Role.DIRECTOR)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access.token.here"))
                .andExpect(jsonPath("$.role").value("DIRECTOR"))
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_returns400_onInvalidEmail() throws Exception {
        RegisterRequest body = RegisterRequest.builder()
                .email("not-an-email")
                .password("securePass1")
                .role(User.Role.DIRECTOR)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400_onShortPassword() throws Exception {
        RegisterRequest body = RegisterRequest.builder()
                .email("director@example.com")
                .password("short")
                .role(User.Role.DIRECTOR)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400_onDuplicateEmail() throws Exception {
        when(authService.register(any())).thenThrow(new BusinessException("Email already registered"));

        RegisterRequest body = RegisterRequest.builder()
                .email("duplicate@example.com")
                .password("securePass1")
                .role(User.Role.STAFF)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /login ───────────────────────────────────────────────────────────

    @Test
    void login_returns200_onValidCredentials() throws Exception {
        when(authService.login(any())).thenReturn(SAMPLE_RESPONSE);

        LoginRequest body = LoginRequest.builder()
                .email("director@example.com")
                .password("securePass1")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void login_returns401_onBadCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest body = LoginRequest.builder()
                .email("director@example.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns400_onMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /refresh ─────────────────────────────────────────────────────────

    @Test
    void refresh_returns200_onValidToken() throws Exception {
        when(authService.refresh(any())).thenReturn(SAMPLE_RESPONSE);

        RefreshRequest body = RefreshRequest.builder().refreshToken("valid.refresh.token").build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void refresh_returns400_onInvalidToken() throws Exception {
        when(authService.refresh(any())).thenThrow(new BusinessException("Invalid or expired refresh token"));

        RefreshRequest body = RefreshRequest.builder().refreshToken("expired.token").build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /logout ──────────────────────────────────────────────────────────

    @Test
    void logout_returns204_onValidToken() throws Exception {
        doNothing().when(authService).logout(any());

        RefreshRequest body = RefreshRequest.builder().refreshToken("valid.refresh.token").build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_returns400_onBlankToken() throws Exception {
        RefreshRequest body = RefreshRequest.builder().refreshToken("").build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
