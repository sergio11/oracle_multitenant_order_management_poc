package com.enterpriseorders.backend.security;

import com.enterpriseorders.backend.config.CredentialHardeningCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class CredentialHardeningCheckTest {

    private static CredentialHardeningCheck check(String... activeProfiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(activeProfiles);
        return new CredentialHardeningCheck(env,
                "admin-test-password-123", "operator-test-password-123");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "   ", "short", "changeme", " changeme ", "11chars-xxx"})
    void weakPasswordsAreDetected(String password) {
        assertTrue(CredentialHardeningCheck.isWeak(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin-test-password-123", "012345678901", "a-very-long-secret-value"})
    void strongPasswordsPass(String password) {
        assertFalse(CredentialHardeningCheck.isWeak(password));
    }

    @Test
    void prodProfileIsStrict() {
        assertTrue(check("prod").isStrict());
    }

    @Test
    void unknownProfileIsStrict() {
        assertTrue(check("staging").isStrict());
        // No application-test.yml exists in this POC: the obsolete "test"
        // alias is strict like any unknown non-local profile.
        assertTrue(check("test").isStrict());
    }

    @Test
    void localProfileIsNotStrict() {
        assertFalse(check("local").isStrict());
        assertFalse(check().isStrict());
    }

    @Test
    void strictModeAbortsOnWeakPassword() {
        CredentialHardeningCheck hardening = check("prod");
        assertThrows(IllegalStateException.class,
                () -> hardening.verify(true, "changeme", "operator-test-password-123"));
        assertThrows(IllegalStateException.class,
                () -> hardening.verify(true, "admin-test-password-123", "short"));
        assertThrows(IllegalStateException.class,
                () -> hardening.verify(true, null, null));
    }

    @Test
    void strictModePassesWithStrongPasswords() {
        assertDoesNotThrow(() -> check("prod")
                .verify(true, "admin-test-password-123", "operator-test-password-123"));
    }

    @Test
    void nonStrictModeOnlyWarnsOnWeakPasswords() {
        assertDoesNotThrow(() -> check("local").verify(false, "changeme", "changeme"));
    }
}
