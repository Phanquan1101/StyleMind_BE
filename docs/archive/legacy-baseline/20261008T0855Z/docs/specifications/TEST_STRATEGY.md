# StyleMind V1 Implementation Test Strategy

Status: IMPLEMENTATION BASELINE

## Layers

- Unit tests for domain rules, money, validation, grounding and idempotency.
- Repository/integration tests with PostgreSQL-compatible Testcontainers where persistence behavior matters.
- API tests for validation, pagination, errors, authorization and public/admin separation.
- Critical workflow tests for Catalog, Cart, Checkout, Payment, Wardrobe and Stylist integration.
- Kafka/outbox tests for committed-state publication and duplicate-safe consumers.

## Required acceptance scenarios

1. Register/login/authentication.
2. Catalog browsing and Product/Variant CRUD.
3. Guest Cart, User Cart and atomic guest-to-user merge.
4. Checkout validation, stock failure, price-change reconfirmation and Order creation.
5. COD, VietQR/SePay attempt, 15-minute expiry, duplicate confirmation idempotency and late-payment terminal-order protection.
6. Cancellation, approved-window return, QC and refund.
7. Wardrobe CRUD and image metadata.
8. AI combines Wardrobe and Catalog, preserves owned/shop provenance, rejects hallucinated Products and rejects hard-constraint violations.

## First-slice gate

Catalog must build, migrate, pass unit/API tests and expose browse/admin behavior before Cart implementation is treated as the next slice. External credentials are replaced with adapter stubs in tests.
