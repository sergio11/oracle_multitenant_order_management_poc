package com.enterpriseorders.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Credential hardening gate (C1).
 *
 * <p>Default credentials ({@code changeme}) exist only for the local profile.
 * Production binds {@code app.security.admin.password} /
 * {@code app.security.operator.password} with NO default
 * ({@code ${APP_ADMIN_PASSWORD}} / {@code ${APP_OPERATOR_PASSWORD}}), so a
 * missing env var already fails context startup on placeholder resolution.
 * This check additionally aborts startup when a non-local profile uses a
 * missing, short (&lt;12 chars) or default ({@code changeme}) password, and
 * logs a single WARN for local/test profiles.
 */
@Component
public class CredentialHardeningCheck {

    private static final Logger log = LoggerFactory.getLogger(CredentialHardeningCheck.class);

    static final int MIN_PASSWORD_LENGTH = 12;
    static final String DEFAULT_PASSWORD = "changeme";

    private final Environment environment;
    private final String adminPassword;
    private final String operatorPassword;

    public CredentialHardeningCheck(Environment environment,
                                    @Value("${app.security.admin.password:}") String adminPassword,
                                    @Value("${app.security.operator.password:}") String operatorPassword) {
        this.environment = environment;
        this.adminPassword = adminPassword;
        this.operatorPassword = operatorPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        verify(isStrict(), adminPassword, operatorPassword);
    }

    /**
     * Strict when the {@code prod} profile is active, or when the active
     * profiles are anything other than local (e.g. staging, test). An empty
     * profile set is treated as non-strict (local developer default).
     *
     * <p>NOTE: there is no {@code application-test.yml} in this POC - the
     * obsolete "test" profile is deliberately treated as strict, like any
     * unknown non-local profile.
     */
    public boolean isStrict() {
        String[] active = environment.getActiveProfiles();
        if (active == null || active.length == 0) {
            return false;
        }
        List<String> profiles = Arrays.asList(active);
        if (profiles.contains("prod")) {
            return true;
        }
        return profiles.stream().noneMatch("local"::equals);
    }

    public void verify(boolean strict, String adminPw, String operatorPw) {
        boolean adminWeak = isWeak(adminPw);
        boolean operatorWeak = isWeak(operatorPw);
        if (strict) {
            if (adminWeak || operatorWeak) {
                throw new IllegalStateException(
                        "Refusing startup: app.security admin/operator passwords must be set, "
                                + "at least " + MIN_PASSWORD_LENGTH + " characters, and not the default '"
                                + DEFAULT_PASSWORD + "' (set APP_ADMIN_PASSWORD / APP_OPERATOR_PASSWORD)");
            }
            log.info("Credential hardening check passed");
        } else if (adminWeak || operatorWeak) {
            log.warn("Weak default credentials in use (non-production profile only); "
                    + "set APP_ADMIN_PASSWORD / APP_OPERATOR_PASSWORD for any shared environment");
        }
    }

    public static boolean isWeak(String password) {
        if (password == null || password.isBlank()) {
            return true;
        }
        String trimmed = password.trim();
        return trimmed.length() < MIN_PASSWORD_LENGTH || DEFAULT_PASSWORD.equals(trimmed);
    }
}
