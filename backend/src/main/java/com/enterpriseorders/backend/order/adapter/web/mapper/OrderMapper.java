package com.enterpriseorders.backend.order.adapter.web.mapper;

import com.enterpriseorders.backend.order.dto.OrderResponseDTO;
import com.enterpriseorders.backend.order.model.OrderBO;
import org.mapstruct.Mapper;

/**
 * BO &rarr; DTO mapping for the order web edge. Replaces the hand-written
 * {@code OrderResponseDTO.from(...)} so every DTO conversion at the edge goes
 * through MapStruct.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponseDTO toResponse(OrderBO order);

    OrderResponseDTO.OrderLineDTO toLine(OrderBO.OrderLineBO line);
}
