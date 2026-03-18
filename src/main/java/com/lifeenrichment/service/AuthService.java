package com.lifeenrichment.service;

import com.lifeenrichment.dto.request.ForgotPasswordRequest;
import com.lifeenrichment.dto.request.LoginRequest;
import com.lifeenrichment.dto.request.RegisterRequest;
import com.lifeenrichment.dto.request.ResetPasswordRequest;
import com.lifeenrichment.dto.response.AuthResponse;
import com.lifeenrichment.entity.PasswordResetToken;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.exception.BusinessException;
import com.lifeenrichment.repository.PasswordResetTokenRepository;
import com.lifeenrichment.repository.UserRepository;
import com.lifeenrichment.security.jwt.JwtUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuditService auditService;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    @Value("${spring.mail.username:noreply@lifeenrichment.app}")
    private String fromEmail;

    private static final long RESET_TOKEN_EXPIRY_MINUTES = 60;

    // ── Auth methods ──────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        String accessToken  = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        auditService.log(null, AuditService.REGISTER, "email=" + user.getEmail() + " role=" + user.getRole().name());
        log.info("New user registered: {}", user.getEmail());
        return buildResponse(accessToken, refreshToken, user.getRole().name());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (Exception e) {
            User failedUser = userRepository.findByEmail(request.getEmail()).orElse(null);
            auditService.log(failedUser, AuditService.LOGIN_FAILED, "email=" + request.getEmail());
            throw e;
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("User not found"));

        String accessToken  = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        auditService.log(user, AuditService.LOGIN_SUCCESS, null);
        log.info("User logged in: {}", user.getEmail());
        return buildResponse(accessToken, refreshToken, user.getRole().name());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new BusinessException("Invalid or expired refresh token");
        }

        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException("Refresh token not recognized — please log in again"));

        String newAccessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        auditService.log(user, AuditService.TOKEN_REFRESHED, null);
        return buildResponse(newAccessToken, refreshToken, user.getRole().name());
    }

    @Transactional
    public void logout(String refreshToken) {
        userRepository.findByRefreshToken(refreshToken).ifPresent(user -> {
            user.setRefreshToken(null);
            userRepository.save(user);
            auditService.log(user, AuditService.LOGOUT, null);
            log.info("User logged out: {}", user.getEmail());
        });
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String rawToken  = UUID.randomUUID().toString();
            String tokenHash = hashToken(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .tokenHash(tokenHash)
                    .userId(user.getId())
                    .expiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String resetLink = appBaseUrl + "/reset-password?token=" + rawToken;
            sendPasswordResetEmail(user.getEmail(), resetLink);

            auditService.log(user, AuditService.PASSWORD_RESET_REQUESTED, null);
            log.info("Password reset email sent to: {}", user.getEmail());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hashToken(request.getToken());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new BusinessException("Reset token has already been used");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Reset token has expired");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        auditService.log(user, AuditService.PASSWORD_RESET_COMPLETED, null);
        log.info("Password reset completed for user: {}", user.getEmail());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void sendPasswordResetEmail(String to, String resetLink) {
        try {
            Context ctx = new Context();
            ctx.setVariable("resetLink", resetLink);
            ctx.setVariable("expiresInMinutes", RESET_TOKEN_EXPIRY_MINUTES);
            String html = templateEngine.process("email/password-reset", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Reset your Life Enrichment App password");
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage());
            throw new BusinessException("Failed to send password reset email");
        }
    }

    private AuthResponse buildResponse(String accessToken, String refreshToken, String role) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtExpirationMs)
                .role(role)
                .build();
    }
}
