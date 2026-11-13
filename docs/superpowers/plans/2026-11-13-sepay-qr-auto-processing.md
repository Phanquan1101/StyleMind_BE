# SePay VietQR Auto Processing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A successfully paid SePay VietQR checkout creates an order already in `PROCESSING`, without an admin confirmation step.

**Architecture:** Keep the existing QR reservation, payment event, and cancellation/refund paths. Change only the order creation transaction after a trusted payment event. Commerce V18 adds `AUTO_CONFIRMED` as a distinct confirmation state with no admin actor. The FE renders the actual payment and preparation history and offers no confirmation action for the QR order.

**Tech Stack:** Spring Boot, PostgreSQL/Flyway, React/TypeScript/Vitest.

**Spec:** Owner decision in this conversation on 2026-11-13: SePay VietQR skips admin confirmation after payment; COD and VNPAY retain their current behavior. Commerce V18 scope explicitly approved.

## Global Constraints

- Preserve existing changes in both repositories and do not run Git commands.
- Do not alter COD or VNPAY order confirmation.
- Do not mutate persistent databases; use isolated tests.
- Keep paid cancellation review and refund behavior.

## Review Focus

- Duplicate payment events must not create duplicate orders or history.
- Checkout cancellation/payment races must not create an order or lose stock.
- An auto-confirmed QR order with a pending cancellation must not advance to shipping.
- Existing `PAID` QR orders must remain readable and retain their previous confirmation flow.
- The customer must see payment followed by preparation in history, without a false admin confirmation.

---

### Task 1: Commerce QR creation and migration

**Files:** V18 migration; `QrCheckoutReservationService.java`; `CartOrderPostgresIntegrationTests.java`; migration test.

- [x] Write failing integration assertions for `PROCESSING`, `AUTO_CONFIRMED`, and exactly two history entries after a paid event and its replay.
- [x] Run the targeted test and confirm it fails for the old `PAID` behavior.
- [x] Add V18 constraints and update QR creation transaction.
- [x] Run targeted and neighboring Commerce tests.

### Task 2: FE order presentation

**Files:** `OrderDetailSections.tsx`; its test; order confirmation state types and any QR-specific admin handling.

- [x] Write failing test for the paid QR history and lack of a confirmation prompt.
- [x] Run targeted FE test and confirm it fails.
- [x] Update presentation and types while preserving existing order behavior.
- [x] Run targeted tests, typecheck, and build.

### Task 3: Review and handoff

- [x] Verify the changed paths through file reads and test output without Git.
- [x] Report exact files, tests, and any runtime checks left for the owner.
