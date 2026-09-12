package com.enterpriseorders.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Enterprise Order Platform - Backend entry point.
 *
 * Spring Boot application exposing a multi-tenant REST API over Oracle.
 * Uses Spring JDBC + Oracle UCP (no JPA) per SRS §50/§51.
 */
@SpringBootApplication
public class EnterpriseOrderPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseOrderPlatformApplication.class, args);
    }
}
</content>
<task_progress>
- [x] FASE 6: Oracle UCP config class
- [x] FASE 6: Clase principal Spring Boot
- [ ] FASE 6: application-local.yml + application-test.yml
- [ ] FASE 6: Global error handler
- [ ] FASE 6: Tenant context (resolver + interceptor)
- [ ] FASE 6: Módulo Tenant (domain/repository/service/controller)
- [ ] FASE 6: Commit fase 6
- [ ] FASE 7 a 12 (resto del plan)
</task_progress>