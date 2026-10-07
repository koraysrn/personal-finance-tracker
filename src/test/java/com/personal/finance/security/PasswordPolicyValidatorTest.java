package com.personal.finance.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-happy-path tests for the password policy (SEC-01, SEC-02).
 */
class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void shortPasswordIsRejected() {
        assertThat(validator.violations("Aa1!aaaa", "user")).isNotEmpty();
    }

    @Test
    void passwordWithoutUppercaseIsRejected() {
        assertThat(validator.violations("alllowercase1!", "user")).isNotEmpty();
    }

    @Test
    void passwordWithoutLowercaseIsRejected() {
        assertThat(validator.violations("ALLUPPERCASE1!", "user")).isNotEmpty();
    }

    @Test
    void passwordWithoutDigitIsRejected() {
        assertThat(validator.violations("NoDigitsHere!", "user")).isNotEmpty();
    }

    @Test
    void passwordWithoutSpecialCharacterIsRejected() {
        assertThat(validator.violations("NoSpecialChar1", "user")).isNotEmpty();
    }

    @Test
    void passwordContainingUsernameIsRejected() {
        assertThat(validator.violations("Ahmet1234!", "ahmet")).isNotEmpty();
    }

    @Test
    void nullPasswordIsRejected() {
        assertThat(validator.violations(null, "user")).isNotEmpty();
    }

    @Test
    void emptyPasswordIsRejected() {
        assertThat(validator.violations("", "user")).isNotEmpty();
    }

    @Test
    void validPasswordHasNoViolations() {
        assertThat(validator.violations("Str0ng!Pass", "ahmet")).isEmpty();
    }
}
