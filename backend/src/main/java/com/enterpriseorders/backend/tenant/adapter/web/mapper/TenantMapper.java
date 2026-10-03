package com.enterpriseorders.backend.tenant.adapter.web.mapper;

import com.enterpriseorders.backend.tenant.dto.TenantRequestDTO;
import com.enterpriseorders.backend.tenant.dto.TenantResponseDTO;
import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;
import org.mapstruct.Mapper;

/**
 * DTO &harr; BO mapping for the tenant web edge. Replaces the hand-written
 * {@code TenantResponseDTO.from(...)} and the manual request construction in
 * {@code TenantController}; {@code NewTenantBO}'s compact constructor still
 * normalizes {@code tenantCode} because MapStruct invokes the canonical
 * record constructor.
 */
@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantResponseDTO toResponse(TenantBO tenant);

    NewTenantBO toNewTenant(TenantRequestDTO request);
}
