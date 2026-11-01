package com.enterpriseorders.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enterprise Order Platform - Backend entry point.
 *
 * Spring Boot application exposing a multi-tenant REST API over Oracle.
 * Uses Spring JDBC + Oracle UCP (no JPA) per SRS §50/§51.
 */
@SpringBootApplication
@EnableScheduling
public class EnterpriseOrderPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseOrderPlatformApplication.class, args);
    }
}
