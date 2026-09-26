package com.enterpriseorders.backend.order.application;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.order.model.OrderView;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore.IdempotencyRecord;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Single choke point for idempotent operations (FR-014).
 *
 * Owns the whole idempotency lifecycle: key validation, lookup, PENDING
 * insert, execution, COMPLETED marking with the serialized response, and
 * replay of the original response on retry.
 *
 * Status handling of an existing record:
 *  - COMPLETED  -> return the stored response payload (no re-execution);
 *                  rows without payload fall back to findById (legacy rows
 *                  and responses too large for response_payload).
 *  - PENDING    -> another request is in flight -> 409 IDEMPOTENCY_IN_PROGRESS.
 *  - FAILED     -> previous attempt rolled back; retry re-executes the action
 *                  and overwrites the row (FAILED is never written by this
 *                  executor - a failed action rolls the whole transaction
 *                  back, PENDING row included).
 *
 * Assumptions: inside a single Spring transaction the PENDING insert is not
 * visible to other connections until commit, and the unique index serializes
 * concurrent inserts - so a re-read after a failed insert only sees rows
 * committed by a transaction that finished first.
 */
@Component
public class IdempotentOperationExecutor {

    private static final int MAX_KEY_LENGTH = 100;
    private static final int ECHO_KEY_PREFIX_LENGTH = 16;
    /** V022 response_payload is VARCHAR2(4000 CHAR); larger payloads degrade to findById replay. */
    private static final int MAX_PAYLOAD_LENGTH = 4000;

    private final IdempotencyStore idempotencyStore;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public IdempotentOperationExecutor(IdempotencyStore idempotencyStore,
                                       OrderRepository orderRepository,
                                       ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    public OrderView execute(Long tenantId, String idempotencyKey, String operation,
                                 Long aggregateId, Supplier<OrderView> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must not exceed " + MAX_KEY_LENGTH + " characters", HttpStatus.BAD_REQUEST);
        }
        var existing = idempotencyStore.findByKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return resolve(tenantId, idempotencyKey, existing.get(), operation, aggregateId, action);
        }
        if (!idempotencyStore.insertPending(tenantId, idempotencyKey, operation, aggregateId)) {
            var raced = idempotencyStore.findByKey(tenantId, idempotencyKey);
            if (raced.isEmpty()) {
                throw new BusinessException("IDEMPOTENCY_RACE_CONFLICT",
                        "Concurrent request in flight for this idempotency key", HttpStatus.CONFLICT);
            }
            return resolve(tenantId, idempotencyKey, raced.get(), operation, aggregateId, action);
        }
        return complete(tenantId, idempotencyKey, action);
    }

    private OrderView resolve(Long tenantId, String idempotencyKey, IdempotencyRecord record,
                                  String operation, Long aggregateId, Supplier<OrderView> action) {
        validateKeyMatches(record, idempotencyKey, operation, aggregateId);
        if ("COMPLETED".equals(record.status())) {
            return replay(tenantId, record);
        }
        if ("FAILED".equals(record.status())) {
            return complete(tenantId, idempotencyKey, action);
        }
        if ("PENDING".equals(record.status())) {
            throw new BusinessException("IDEMPOTENCY_IN_PROGRESS",
                    "Another request with this idempotency key is still in progress", HttpStatus.CONFLICT);
        }
        throw new IllegalStateException("Unexpected idempotency status: " + record.status());
    }

    private OrderView replay(Long tenantId, IdempotencyRecord record) {
        String payload = record.responsePayload();
        if (payload != null && !payload.isBlank()) {
            try {
                return objectMapper.readValue(payload, OrderView.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(
                        "Stored idempotent response could not be deserialized", e);
            }
        }
        if (record.aggregateId() != null && record.aggregateId() > 0) {
            return orderRepository.findById(tenantId, record.aggregateId());
        }
        throw new BusinessException("IDEMPOTENCY_PAYLOAD_MISSING",
                "Original response for this idempotency key is unavailable", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private OrderView complete(Long tenantId, String idempotencyKey, Supplier<OrderView> action) {
        OrderView response = action.get();
        int updated = idempotencyStore.markCompleted(tenantId, idempotencyKey, serializeForStorage(response));
        if (updated != 1) {
            throw new IllegalStateException(
                    "Idempotency completion updated " + updated + " row(s) for operation");
        }
        return response;
    }

    private String serializeForStorage(OrderView response) {
        String json = serialize(response);
        return json.length() > MAX_PAYLOAD_LENGTH ? null : json;
    }

    private String serialize(OrderView response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private void validateKeyMatches(IdempotencyRecord record, String idempotencyKey,
                                    String operation, Long aggregateId) {
        if (!Objects.equals(operation, record.operation())
                || !Objects.equals(aggregateId, record.aggregateId())) {
            throw new BusinessException("IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency key '" + truncate(idempotencyKey) + "' was already used for a "
                            + "different operation or order; it cannot be reused", HttpStatus.CONFLICT);
        }
    }

    private String truncate(String idempotencyKey) {
        return idempotencyKey.length() <= ECHO_KEY_PREFIX_LENGTH
                ? idempotencyKey
                : idempotencyKey.substring(0, ECHO_KEY_PREFIX_LENGTH) + "...";
    }
}
