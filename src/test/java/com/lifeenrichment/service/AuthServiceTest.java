package com.lifeenrichment.service;

import com.lifeenrichment.dto.request.ForgotPasswordRequest;
import com.lifeenrichment.dto.request.ResetPasswordRequest;
import com.lifeenrichment.entity.PasswordResetToken;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.repository.PasswordResetTokenRepository;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.security.jwt.JwtUtils;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock AuthenticationManager authenticationManager;
    @Mock JavaMailSender mailSender;
    @Mock TemplateEngine templateEngine;

    @InjectMocks AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpirationMs", 86400000L);
        ReflectionTestUtils.setField(authService, "appBaseUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(authService, "fromEmail", "noreply@test.com");

        testUser = User.builder()
                .email("user@example.com")
                .passwordHash("$2a$10$hashed")
                .role(User.Role.STAFF)
                .build();
        ReflectionTestUtils.setField(testUser, "id", UUID.randomUUID());
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Test
    void forgotPassword_sendsEmail_whenUserExists() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>reset</html>");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        authService.forgotPassword(new ForgotPasswordRequest("user@example.com"));

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();

        assertThat(saved.getTokenHash()).isNotBlank().hasSize(64); // SHA-256 hex = 64 chars
        assertThat(saved.getUserId()).isEqualTo(testUser.getId());
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(saved.isUsed()).isFalse();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void forgotPassword_silentSuccess_whenUserNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("unknown@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_updatesPassword_onValidToken() {
        String rawToken  = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(computeHash(rawToken))
                .userId(testUser.getId())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .used(false)
                .build();

        when(passwordResetTokenRepository.findByTokenHash(computeHash(rawToken)))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword1")).thenReturn("$2a$10$newHash");

        authService.resetPassword(new ResetPasswordRequest(rawToken, "newPassword1"));

        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("$2a$10$newHash")));
        assertThat(token.isUsed()).isTrue();
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPassword_throwsBusinessException_onExpiredToken() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(computeHash(rawToken))
                .userId(testUser.getId())
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // already expired
                .used(false)
                .build();

        when(passwordResetTokenRepository.findByTokenHash(computeHash(rawToken)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() ->
                authService.resetPassword(new ResetPasswordRequest(rawToken, "newPassword1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPassword_throwsBusinessException_onAlreadyUsedToken() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(computeHash(rawToken))
                .userId(testUser.getId())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .used(true) // already used
                .build();

        when(passwordResetTokenRepository.findByTokenHash(computeHash(rawToken)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() ->
                authService.resetPassword(new ResetPasswordRequest(rawToken, "newPassword1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void resetPassword_throwsBusinessException_onUnknownToken() {
        String rawToken = UUID.randomUUID().toString();
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.resetPassword(new ResetPasswordRequest(rawToken, "newPassword1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid");
    }

    // ── helper (mirrors AuthService.hashToken for test assertions) ────────────

    private String computeHash(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
