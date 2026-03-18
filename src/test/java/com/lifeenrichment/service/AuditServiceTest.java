package com.lifeenrichment.service;

import com.lifeenrichment.entity.AuditLog;
import com.lifeenrichment.entity.User;
import com.lifeenrichment.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditLogRepository auditLogRepository;

    @InjectMocks AuditService auditService;

    private User buildUser(String email) {
        User user = User.builder()
                .email(email)
                .passwordHash("hash")
                .role(User.Role.DIRECTOR)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private void assertLogSaved(String expectedAction, User expectedUser) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(expectedAction);
        assertThat(saved.getEntityType()).isEqualTo("AUTH");
        assertThat(saved.getUser()).isEqualTo(expectedUser);
    }

    @Test
    void log_register_savesCorrectAction() {
        User user = buildUser("director@example.com");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(user, AuditService.REGISTER, "role=DIRECTOR");

        assertLogSaved(AuditService.REGISTER, user);
    }

    @Test
    void log_loginSuccess_savesCorrectAction() {
        User user = buildUser("staff@example.com");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(user, AuditService.LOGIN_SUCCESS, null);

        assertLogSaved(AuditService.LOGIN_SUCCESS, user);
    }

    @Test
    void log_loginFailed_savesCorrectActionWithNullUser() {
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(null, AuditService.LOGIN_FAILED, "email=unknown@example.com");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(AuditService.LOGIN_FAILED);
        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getDetails()).isEqualTo("email=unknown@example.com");
    }

    @Test
    void log_logout_savesCorrectAction() {
        User user = buildUser("user@example.com");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(user, AuditService.LOGOUT, null);

        assertLogSaved(AuditService.LOGOUT, user);
    }

    @Test
    void log_tokenRefreshed_savesCorrectAction() {
        User user = buildUser("user@example.com");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(user, AuditService.TOKEN_REFRESHED, null);

        assertLogSaved(AuditService.TOKEN_REFRESHED, user);
    }

    @Test
    void log_passwordResetRequested_savesCorrectAction() {
        User user = buildUser("user@example.com");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(user, AuditService.PASSWORD_RESET_REQUESTED, null);

        assertLogSaved(AuditService.PASSWORD_RESET_REQUESTED, user);
    }

    @Test
    void log_passwordResetCompleted_savesCorrectAction() {
        User user = buildUser("user@example.com");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(user, AuditService.PASSWORD_RESET_COMPLETED, null);

        assertLogSaved(AuditService.PASSWORD_RESET_COMPLETED, user);
    }
}
