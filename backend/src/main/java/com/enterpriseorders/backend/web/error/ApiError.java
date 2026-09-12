package com.enterpriseorders.backend.web.error;

/**
 * Consistent API error payload (SRS §54).
 *
 * Example:
 *   {
 *     "code": "INSUFFICIENT_INVENTORY",
 *     "message": "Insufficient inventory for product",
 *     "correlationId": "..."
 *   }
 *
 * Internal Oracle errors (ORA-*) are never exposed to the consumer.
 */
public record ApiError(
        String code,
        String message,
        String correlationId
) {
    public static ApiError of(String code, String message, String correlationId) {
        return new ApiError(code, message, correlationId);
    }
}
</content>
<task_progress>
- [x] FASE 6: Oracle UCP config class
- [x] FASE 6: Clase principal Spring Boot
- [x] FASE 6: application-local.yml (base con UCP + actuator)
- [x] FASE 6: ApiError record
- [ ] FASE 6: GlobalExceptionHandler
- [ ] FASE 6: Tenant context (resolver + interceptor)
- [ ] FASE 6: Módulo Tenant (domain/repository/service/controller)
- [ ] FASE 6: Commit fase 6
- [ ] FASE 7 a 12 (resto del plan)
</task_progress>