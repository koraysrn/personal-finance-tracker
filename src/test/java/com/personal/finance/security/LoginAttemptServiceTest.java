package com.personal.finance.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Brute force lockout logic (SEC-03, SEC-04).
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private AppUserRepository userRepository;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(userRepository);
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(service, "lockDurationMinutes", 15L);
    }

    @Test
    void fiveFailuresLockTheAccount() {
        AppUser user = new AppUser();
        user.setUsername("user");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));

        for (int i = 0; i < 5; i++) {
            service.recordFailure("user");
        }

        assertThat(user.getFailedLoginCount()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    void lockIsNotAppliedBeforeThreshold() {
        AppUser user = new AppUser();
        user.setUsername("user");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));

        service.recordFailure("user");

        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void successfulLoginResetsCounterAndLock() {
        AppUser user = new AppUser();
        user.setUsername("user");
        user.setFailedLoginCount(5);
        user.setLockedUntil(java.time.Instant.now());
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));

        service.recordSuccess("user");

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void unknownUsernameIsSilentlyIgnored() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        service.recordFailure("nobody");

        verify(userRepository, never()).save(any());
    }
}
