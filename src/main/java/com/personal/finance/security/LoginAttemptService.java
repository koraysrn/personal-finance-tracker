package com.personal.finance.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Brute force protection: tracks consecutive failed login attempts per user
 * and temporarily locks the account once the threshold is exceeded.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final AppUserRepository userRepository;

    @Value("${app.security.max-failed-login-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-duration-minutes:15}")
    private long lockDurationMinutes;

    /**
     * Records a failed login attempt for the given username.
     * If the account does not exist the call is silently ignored so that the
     * response cannot be used to enumerate registered usernames.
     */
    @Transactional
    public void recordFailure(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= maxFailedAttempts) {
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockDurationMinutes)));
            }
            userRepository.save(user);
        });
    }

    /**
     * Clears the failure counter after a successful login.
     */
    @Transactional
    public void recordSuccess(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }
}
