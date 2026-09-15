package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.dto.TenantRequest;
import com.enterpriseorders.backend.tenant.dto.TenantResponse;
import com.enterpriseorders.backend.tenant.port.input.CreateTenantUseCase;
import com.enterpriseorders.backend.tenant.port.input.GetTenantQuery;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant REST API (SRS §52, §53).
 *
 * POST /api/v1/tenants  -> create tenant (FR-001)
 * GET  /api/v1/tenants/{id} -> retrieve tenant
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final CreateTenantUseCase createTenantUseCase;
    private final GetTenantQuery getTenantQuery;

    public TenantController(CreateTenantUseCase createTenantUseCase, GetTenantQuery getTenantQuery) {
        this.createTenantUseCase = createTenantUseCase;
        this.getTenantQuery = getTenantQuery;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createTenantUseCase.createTenant(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable Long id) {
        return ResponseEntity.ok(getTenantQuery.getTenant(id));
    }
}