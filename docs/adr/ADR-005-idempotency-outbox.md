# ADR-005 — Database-Level Idempotency + Outbox

Status: Accepted

## Context
Retries/timeouts must not double-reserve stock or double-pay (SRS §62,
FR-014); business mutation and event emission must stay consistent (§63).

## Decision
`idempotency_key` table, unique `(tenant_id,idempotency_key)`, key bound to
operation+aggregate (misuse → 409). `IdempotentOperationExecutor` owns full
lifecycle: race→409, replay returns ORIGINAL stored payload (V022 4000 CHAR;
oversize falls back to findById), PENDING→409, FAILED→re-execute.
Create/confirm/cancel/payment accept `Idempotency-Key`; add-line is
deliberately excluded for now (no key on `POST /{id}/lines`: a retried
add-line inserts a second row — callers must not blindly retry it; a key or
a unique `(order_id, product_id)` guard is backlog). For `CREATE_ORDER` the
aggregate id is the `0` sentinel and replay additionally checks the request
fingerprint (customer+currency) against the stored payload: same key with
different params → 409 instead of silently returning the first order.
Outbox rows written by PL/SQL in the same transaction via `JSON_OBJECT`;
dedup unique `(tenant_id,aggregate_type,aggregate_id,event_type)` (V021);
`pkg_audit` autonomous; publisher out of scope (rows stay NEW).

## Consequences
+ Safe retries; atomic business+event. − Payload >4000 degrades replay;
no relayer yet.
