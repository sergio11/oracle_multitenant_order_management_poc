package com.enterpriseorders.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints: actuator lives under the management
                // base-path /internal (see application.yml), so the real
                // probe paths are /internal/health/**. There is no
                // /actuator/** path in this service.
                // K8s probes poll /internal/health/liveness and /readiness.
                .requestMatchers("/internal/health", "/internal/health/**").permitAll()
                // Tenant provisioning/reads are admin-only (L15).
                .requestMatchers("/api/v1/tenants/**").hasRole("ADMIN")
                // Order operations require ORDER_OPERATOR role
                .requestMatchers("/api/v1/orders/**").hasRole("ORDER_OPERATOR")
                // Reporting refresh requires ADMIN role
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/refresh").hasRole("ADMIN")
                // Reporting reads require ORDER_OPERATOR role
                .requestMatchers("/api/v1/reports/**").hasRole("ORDER_OPERATOR")
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {});
        return http.build();
    }

    // NOTE (C1): no code defaults here. local/test profiles supply values
    // via application.yml; prod (application-prod.yml) binds with NO default
    // (${APP_ADMIN_PASSWORD} / ${APP_OPERATOR_PASSWORD}) so the context
    // fails fast when the env vars are missing. CredentialHardeningCheck
    // additionally aborts prod startup on weak values.
    @Value("${app.security.admin.password}")
    private String adminPassword;

    @Value("${app.security.operator.password}")
    private String operatorPassword;

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var admin = User.builder()
            .username("admin")
            .password(encoder.encode(adminPassword))
            .roles("ADMIN", "ORDER_OPERATOR")
            .build();
        var operator = User.builder()
            .username("operator")
            .password(encoder.encode(operatorPassword))
            .roles("ORDER_OPERATOR")
            .build();
        return new InMemoryUserDetailsManager(admin, operator);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
