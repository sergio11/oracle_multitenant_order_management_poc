--=====================================================================
-- V021 - Outbox dedup key: drop mutable STATUS, add TENANT_ID (M7)
--
-- PROBLEM (V008): uq_outbox_aggregate_event UNIQUE (aggregate_type,
-- aggregate_id, event_type, status) has two defects:
--   1. STATUS is mutable (NEW -> PROCESSING -> PUBLISHED): as soon as a
--      publisher marks an event PUBLISHED the uniqueness slot frees up
--      and a duplicate event for the same aggregate can be inserted.
--   2. TENANT_ID is missing: aggregate ids are per-tenant sequences, so
--      the business key of "one event per tenant/aggregate/type, ever"
--      is not expressed at all.
--
-- FIX: replace it with a single UNIQUE constraint on the immutable
-- business key (tenant_id, aggregate_type, aggregate_id, event_type).
--
-- NOTE (known risk): if an existing database already holds duplicates
-- of the new key (e.g. a NEW and a PUBLISHED row for the same event),
-- ADD CONSTRAINT fails with ORA-02299 (found duplicate records).
-- Fresh POC databases are clean by construction (events are written
-- atomically with the state change that produces them); a polluted QA
-- database must be re-seeded - documented for QA/DOCS.
--=====================================================================
ALTER TABLE outbox_event DROP CONSTRAINT uq_outbox_aggregate_event;

ALTER TABLE outbox_event ADD CONSTRAINT uq_outbox_dedup_event
    UNIQUE (tenant_id, aggregate_type, aggregate_id, event_type);
