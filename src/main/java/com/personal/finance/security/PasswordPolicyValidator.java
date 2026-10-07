package com.personal.finance.security;

import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Enforces the password policy:
 * minimum length, character class requirements and a check that the password
 * does not contain the username. Kept as a pure component for easy unit testing.
 */
@Component
public class PasswordPolicyValidator {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    /**
     * Returns the list of human readable violations. Empty list means the
     * password satisfies the policy.
     */
    public List<String> violations(String rawPassword, String username) {
        List<String> result = new ArrayList<>();
        if (rawPassword == null || rawPassword.isEmpty()) {
            result.add("Password must not be empty");
            return result;
        }
        if (rawPassword.length() < MIN_LENGTH) {
            result.add("Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            result.add("Password must not exceed " + MAX_LENGTH + " characters");
        }
        if (!rawPassword.chars().anyMatch(Character::isUpperCase)) {
            result.add("Password must contain at least one uppercase letter");
        }
        if (!rawPassword.chars().anyMatch(Character::isLowerCase)) {
            result.add("Password must contain at least one lowercase letter");
        }
        if (!rawPassword.chars().anyMatch(Character::isDigit)) {
            result.add("Password must contain at least one digit");
        }
        if (rawPassword.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
            result.add("Password must contain at least one special character");
        }
        if (username != null && !username.isBlank()
                && rawPassword.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            result.add("Password must not contain the username");
        }
        return result;
    }

    /**
     * Validates and throws {@link ApiException} with {@code PASSWORD_POLICY_VIOLATION}
     * when the policy is not met.
     */
    public void validate(String rawPassword, String username) {
        List<String> violations = violations(rawPassword, username);
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATION, String.join("; ", violations));
        }
    }
}
