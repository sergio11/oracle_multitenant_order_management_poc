# ADR-003 — PL/SQL-Authoritative Business Logic (Java Domain Deleted)

Status: Accepted

## Context
Two competing authorities existed: Java domain (`order/domain`,
`Money`/`Quantity`) documenting the state machine vs PL/SQL packages
actually enforcing it. The Java aggregate was never instantiated on the
request path — dead code that invited divergence.

## Decision
PL/SQL is the single authority (`pkg_order_management`, `pkg_inventory`,
`pkg_tenant`, `pkg_audit`): atomic validate→lock→reserve→transition→audit→
outbox in one transaction; exact-total guard (-20035), quantity guard
(-20036), `%ROWCOUNT` transition guard (-20038). Deleted `order/domain`,
`Money`/`Quantity`; kept `PaymentMethod` literal enum + consistency test
(DB check constraint is the source of truth).

## Consequences
+ No dual state machine; ACID per operation. − Logic versioned via
Flyway/packages; heavier DB testing (SQL suites).
