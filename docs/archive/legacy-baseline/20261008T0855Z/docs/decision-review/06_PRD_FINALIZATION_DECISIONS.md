# StyleMind PRD Finalization Decisions

Status: HUMAN DECISIONS RECORDED — PRD UPDATED

Purpose: preserve the minimum product/business decision packet and its recorded human outcome. The five decisions below are now authoritative for PRD finalization; residual SAD/security/runtime-only questions remain deferred.

Decision authority: Phan Đoàn Thế Quân — Solo Owner

## Why these items blocked PRD finalization

The current PRD can describe the confirmed happy paths, but the items below change user-visible outcomes in core commerce flows. The authoritative sources intentionally leave those outcomes open. Finalizing them without a human choice would invent business behavior.

## Required decisions

| Decision cluster | Resolved product question needed for PRD | Existing confirmed constraints | OPEN IDs |
| --- | --- | --- | --- |
| DR-005 Cart behavior | What happens when guest/user carts merge with duplicate quantities, insufficient stock, inactive Variants, changed prices, invalid items or partial failure? Is the guest cart cleared or retired in each outcome? | One active user cart; guest cart has no userId; duplicate Variant quantities combine; cart add validates active/in-stock Variant and quantity. | OPEN-009, OPEN-010 |
| DR-006 Stock and checkout guarantees | What customer-visible outcome applies when stock changes during checkout, order creation or payment initiation? Which failures show retry, abort, partial result or recovery? | Checkout requires current address/cart/Variant/stock/price checks; backend totals are authoritative; duplicate successful effects must be prevented. | OPEN-011, OPEN-014 |
| DR-007 Money interpretation | What currency, rounding, effective price, shipping fee, tax activation and refund amount semantics must the customer see and be charged? | Backend totals are authoritative; payment reconciliation uses the backend-created amount; supplied hard AI budget must be obeyed. | OPEN-012 |
| DR-008 Payment/order reconciliation | How are COD collection, SePay under/over/unmatched/late payment, expiry, cancellation races and terminal order states presented and resolved? | Frontend cannot confirm online payment; verified webhook confirms matched payment; duplicate webhooks must not repeat effects; terminal order states cannot transition onward. | OPEN-015 through OPEN-019 |
| DR-009 Cancellation/return/refund/payout | What are cancellation eligibility, return window/states, QC outcomes, exchange/replacement scope, refund amount/state rules and payout handling? | Customer cancellation requires eligible state and reason; returns validate ownership/state/window/quantity; admin rejection needs a reason; refund cannot exceed valid amount. | OPEN-021, OPEN-023 through OPEN-027 |

## Explicitly excluded from this packet

The following remain deferred but do not block PRD finalization by themselves: JWT/key lifecycle, final bounded contexts, data ownership, sync/async choices, deployment, AI provider/runtime, Qdrant/Neo4j ownership, knowledge synchronization, detailed threat controls, accessibility targets and implementation mechanisms.

## Recorded human approval outcome

The human owner approved Option A for all five clusters on 2026-09-21. The authoritative records were allocated after the existing highest DEC-029 and are preserved in `docs/decisions/DEC-030.md` through `DEC-034.md`.

| Decision cluster | Authoritative DEC ID | Approval outcome | Residual TBD/deferred detail |
| --- | --- | --- | --- |
| DR-005 | DEC-030 | Atomic Cart Merge | OPEN-009 partial: guest-session lifecycle/security, quantity bounds and update semantics. |
| DR-006 | DEC-031 | Atomic Product-Level Checkout | OPEN-011 and OPEN-014 partial: stock mechanism, ownership, compensation and detailed recovery. |
| DR-007 | DEC-032 | Minimal V1 Money Policy | No PRD-blocking residual; unrelated AI/reporting details remain governed elsewhere. |
| DR-008 | DEC-033 | Strict Authoritative Payment Confirmation | OPEN-015/018/019 partial and OPEN-016 deferred: COD operations, actor/event mapping, Order-timeout coordination and webhook protocol. |
| DR-009 | DEC-034 | Conservative V1 Cancellation/Return/Refund Policy | OPEN-021/023/024/025/027 partial: request/concurrency, QC/restock, shipment/carrier and payout access details. |

The PRD was updated to incorporate these approved policies. The PRD finalization blocker was removed, and the PRD was subsequently HUMAN-APPROVED by Phan Đoàn Thế Quân — Solo Owner on 2026-09-22. This packet remains decision evidence and does not approve the Domain Model or later artifacts.
