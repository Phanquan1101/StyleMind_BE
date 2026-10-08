# StyleMind Business Requirements Document

Status: HUMAN-APPROVED (APPROVED) — grounded in the Human Decision Baseline V1

Prepared: 2026-09-21

Approved by: Phan Đoàn Thế Quân — Solo Owner

Approval date: 2026-09-21

Decision authority: Phan Đoàn Thế Quân — Solo Developer & Product Owner

This document is the first business-level specification after Phase 0 and Phase 0.5. It is not a PRD, Domain Model, SAD, API contract, database schema, event catalog, security architecture or implementation plan.

## 1. Authority and source basis

This BRD is grounded in:

- The original StyleMind input set under `docs/input/`.
- The Phase 0 discovery package under `docs/discovery/`.
- The approved Human Decision Baseline V1 under `docs/decisions/00_DECISION_BASELINE_V1.md`.
- DEC-026 through DEC-034.

The new authoritative decision IDs were allocated because DEC-001 through DEC-025 already exist as historical decisions with different meanings. No historical decision was modified or superseded.

The BRD uses the following status language:

- CONFIRMED: supported by the source/discovery baseline or an approved decision.
- APPROVED: explicitly selected by the human decision authority.
- TBD / OPEN / DEFERRED: not yet specified or intentionally left for a later artifact.
- DERIVED: analytical linkage, not a new requirement.

## 2. Product summary

StyleMind is a women-first fashion commerce platform with an AI Personal Stylist and Smart Wardrobe. It connects a user's context and owned clothing with real StyleMind catalog products, outfit recommendations and a complete commerce lifecycle.

The product is intended to help a customer move from a fashion need to a useful, explainable outfit suggestion and, where appropriate, to a real purchasable Product/Variant. The same product also supports cart, checkout, payment, order and post-purchase operations.

V1 serves female customers with diverse fashion styles. It is not restricted to one predefined aesthetic. V1 is a substantial personal project rather than a tiny demo, but ideas outside the approved V1 boundary do not enter V1 automatically.

## 3. Problem and opportunity

Customers may have difficulty selecting products, combining existing clothes with new purchases, respecting a budget and understanding why a recommendation is appropriate. A generic fashion chatbot does not solve the commerce problem if it suggests products that cannot be found or bought, and a catalog without contextual styling does not solve the outfit-composition problem.

StyleMind addresses this gap by connecting:

**User context → Wardrobe → Catalog → Outfit Recommendation → Commerce.**

The product opportunity is the integration of personalized styling and trustworthy commerce, not a claim that StyleMind invents a new AI model or a new fashion science.

## 4. Business objectives and success baseline

### 4.1 Objectives

- Combine fashion commerce with AI styling connected to purchasable products (BUS-001).
- Specialize primarily in women's styling, wardrobe, fit, color and related context while supporting diverse styles (BUS-002; DEC-027).
- Retain the purchase, payment, post-purchase and administration lifecycle (BUS-003).
- Enable operational investigation and reconciliation through important-action traceability (BUS-004).

### 4.2 Approved acceptance baseline

1. Commerce correctness follows Product → Variant → Cart → Checkout → Payment → Order.
2. Stylist usefulness meaningfully connects User Context + Wardrobe + Catalog + Outfit Recommendation + Commerce.
3. Every product presented by AI as purchasable during acceptance testing resolves to a real StyleMind Catalog Product/Variant entity.
4. Applicable hard constraints are obeyed. Soft preferences are evaluated for relevance; hard constraints override aesthetic preference.

The current business KPI policy uses technical/functional acceptance-level Pass/Fail metrics. Conversion, retention, revenue, satisfaction and arbitrary AI-accuracy targets remain TBD.

## 5. Actors

### CUSTOMER

The primary customer uses account/profile, address, catalog, cart, checkout, payment, order, return/refund, notification, Smart Wardrobe and AI Stylist capabilities (ACT-001).

### GUEST

A guest may use a cart without a userId. Checkout requires authentication (ACT-003; FR-012, FR-016).

### ADMIN

The administrator manages users, catalog, orders, returns/refunds, notifications, AI knowledge and operational reports, subject to admin authorization and audit requirements (ACT-002; FR-008, FR-010, FR-033 through FR-036).

### SYSTEM AUTOMATION

The system performs verification, price/stock checks, totals, payment webhook reconciliation, expiry, notifications, logging and conditional AI synchronization. This is system behavior, not a newly created user role (ACT-004).

### Decision authority

Phan Đoàn Thế Quân is the final product/business and technical/architecture decision maker under DEC-026. Instructor/advisor feedback is stakeholder input and becomes authoritative only when explicitly approved by the Solo Owner.

## 6. V1 scope

### 6.1 Women-first customer experience

The product is designed primarily for female customers and diverse fashion styles. It may use approved user context such as occasion, intended use, style, brand preference, color and optional fit/body information. Optional body measurements are not mandatory registration fields (DEC-027; BR-033).

### 6.2 Commerce Core

The approved V1 commerce core is:

- Product and Variant Catalog.
- Product/category discovery and filtering.
- Guest and authenticated carts.
- Checkout.
- COD and SePay/VietQR payment flows already present in the source baseline.
- Order creation and state tracking.

The supporting confirmed commerce lifecycle remains applicable: account and address management, notifications, order history, cancellation, returns, refunds, administration, reporting and audit. Naming the V1 commerce core does not remove those confirmed requirements.

### 6.3 Smart Wardrobe

Smart Wardrobe V1 includes:

- Create/add a wardrobe item.
- View a wardrobe item.
- Update a wardrobe item.
- Delete a wardrobe item.
- Personal clothing image upload.
- Basic metadata: category, primary color, occasion and/or style.
- List/grid or equivalent visual browsing.
- Category-based organization.
- Use of wardrobe items as AI context.
- A clear distinction between `YOU ALREADY OWN` and `RECOMMENDED TO BUY`.

Initial category concepts are Tops/Áo, Bottoms/Quần, Dresses/Váy, Shoes/Giày and Accessories/Phụ kiện. These are product concepts, not a final database enum design.

Owned wardrobe items resolve from Wardrobe data. Purchasable recommendations resolve from Catalog data.

### 6.4 AI Stylist

V1 AI Stylist can:

- Recommend outfits using occasion, intended use, style and general weather context manually supplied by the user.
- Reuse appropriate Wardrobe items.
- Combine owned Wardrobe items with real Catalog products.
- Respect an explicitly supplied hard maximum budget.
- Obey other applicable hard constraints such as excluded category, prohibited color or excluded product/type.
- Explain selection reasons and provide styling tips consistent with the existing AI requirements (AI-005).
- Return purchasable products only when they map to real Catalog Product/Variant entities.

The AI output must distinguish owned items from items recommended for purchase. A Wardrobe item must not be represented as a Shop item, and a Shop item must not be represented as already owned.

## 7. Explicit V1 limitations and exclusions

The following are not V1 requirements:

- Sophisticated computer vision.
- Automatic advanced body analysis.
- Complex background extraction.
- Garment segmentation from cluttered images.
- Automatic Wardrobe extraction from full-body selfies.
- Automatic recognition of clothing from body photos.
- Wear-history tracking, outfit wear diary or laundry state.
- Wardrobe analytics and cost-per-wear analytics.
- Exact size/fit guarantee. Size/fit guidance is advisory and the customer makes the final size selection before adding to cart.
- Deep skin-tone analysis or professional Personal Color Analysis.
- Real-time weather API as a V1 requirement.
- Automatic multi-day travel outfit planning.
- Automatic packing optimization.
- A model/provider choice, exact AI latency/cost target or arbitrary subjective-quality percentage.

Advanced live 3D remains broader product direction with no automatic V1 commitment. 2D composition, 3D avatar visualization and virtual try-on remain distinct concepts; no photorealistic try-on, body scanning or clothing segmentation is implied.

## 8. Core business flows

### 8.1 Account, profile and address

1. The customer registers with email and verifies a correct, unexpired OTP before a real account is created (FR-001; BR-001).
2. The customer authenticates with email/password after account status checks and receives the source-defined JWT behavior (FR-002; SEC-003 through SEC-005).
3. The customer may reset a password through the approved reset flow after OTP verification (FR-003). The exact reset alternative and complete token/session lifecycle remain TBD.
4. The customer views or updates the basic profile and manages shipping addresses (FR-004, FR-005).
5. Address ownership and valid checkout address are enforced (BR-005). Vietnam province/ward data and phone/address normalization remain subject to the unresolved policy details in OPEN-007.

### 8.2 Catalog discovery and administration

1. A customer views public categories and searches/filters products by keyword, category, price and demographic data (FR-007, FR-009).
2. Product details include relevant variants and source-supported attributes such as SKU, size, color, material, stock, status, images and effective price (FR-009, FR-011, DATA-001).
3. An admin manages categories, products, variants, statuses and product images (FR-008, FR-010).
4. Product/Variant/SKU meaning, activation/deletion safeguards and category lifecycle details remain TBD under OPEN-008 and OPEN-050.

### 8.3 Cart

1. A guest cart is associated with a client guest session identifier and has no userId (FR-012).
2. An authenticated customer has an active user cart (FR-013).
3. The customer adds, updates, removes or clears cart items. Add operations validate active Product/Variant state, stock and quantity (FR-014; BR-010).
4. After login, the guest cart can merge with the user cart. Duplicate variants combine quantities and the guest cart is cleared or retired according to the source rule (FR-015; BR-011).
5. Quantity limits, session security, overflow, price changes, retry and partial-failure behavior remain TBD under OPEN-009 and OPEN-010.

### 8.4 Checkout, payment and order

1. Checkout requires login, a nonempty cart, valid address, active Product/Variant data, stock and refreshed prices (FR-016; BR-012).
2. Backend totals are authoritative; frontend prices are not trusted (BR-008, BR-015).
3. Checkout creates order/items with purchase-time price and address/recipient snapshots (FR-016; DATA-002; BR-014).
4. COD creates a pending payment transaction and confirms the order. Cart clearing and notification are ancillary best-effort effects and do not roll back the order (FR-017; BR-016, BR-017).
5. SePay/VietQR creates a pending payment transaction and exposes transfer/QR information (FR-018).
6. SePay confirmation is received from a verified webhook, with amount/content matching and duplicate protection (FR-019; BR-018 through BR-020).
7. The explicit source order state machine and invalid-transition business error remain applicable (FR-020; BR-021, BR-022).
8. Payment timeout/expiry is supported (FR-021), while timeout duration and abnormal-money handling remain TBD.

Stock mutation, transaction boundaries, payment/order races, compensation and recovery remain deferred under OPEN-011, OPEN-014, OPEN-017, OPEN-018, OPEN-019 and OPEN-054.

### 8.5 Orders, cancellation, returns and refunds

1. Customers view their orders, items, purchase prices and current status (FR-022).
2. Eligible customer cancellations include a reason; admin cancellation includes an operational reason and follows the order state machine (FR-023, FR-024).
3. Customers may upload eligible receipt evidence subject to ownership and image validation (FR-025; BR-025).
4. A return checks order ownership, eligible state, return window and remaining quantity (FR-026; BR-026, BR-027).
5. A return request may include item quantity, reason, description and evidence for admin review (FR-027).
6. Admin inspects, approves/rejects and records QC, followed by refund/restock decisions where applicable (FR-028; BR-028).
7. Return shipment information can be recorded in an eligible state (FR-029; BR-029).
8. Refund requests are associated with the order/return and can be marked complete or failed by an authorized admin (FR-030; BR-030).
9. A payout destination is linked to the corresponding return request (FR-031; BR-031).

Return windows, exchange/replacement scope, QC policy, refund formulas, payout verification and external carrier/bank integrations remain TBD under OPEN-021 and OPEN-023 through OPEN-027.

### 8.6 Smart Wardrobe flow

1. The customer adds a personal clothing item.
2. The customer supplies the approved basic metadata and may upload a clothing image.
3. The customer browses items visually and by category.
4. The customer edits or deletes an item.
5. The AI Stylist receives approved Wardrobe context.
6. The response distinguishes `YOU ALREADY OWN` items from `RECOMMENDED TO BUY` catalog items.
7. Each buyable item maps to a real Catalog Product/Variant.

The approved acceptance outcome is that a saved Wardrobe item can be validly used together with real Catalog items in an outfit. Image limits, storage/retention/access rules and the final data model remain TBD.

### 8.7 AI Stylist flow

1. The customer provides context such as occasion, intended use, style, general manually supplied weather context, budget and restrictions.
2. The AI interprets context and considers approved Wardrobe and Catalog sources.
3. The AI composes an outfit subject to hard constraints.
4. The AI identifies which items are owned and which are recommended for purchase.
5. The AI explains selection reasons and provides styling tips.
6. Purchasable products are validated against real Catalog Product/Variant entities.
7. The customer makes the final product/size selection before adding an item to the cart.

Scenario-based Pass/Fail acceptance must fail hallucinated products, invalid Catalog mappings, incorrect owned/Shop labeling or hard-constraint violations. Subjective fashion-quality thresholds remain TBD.

### 8.8 Administration, notifications, knowledge and audit

The admin manages users, catalog, order/return/refund operations, notifications and operational reporting. Customers can view notifications and unread state. Admin notification logs include delivery status and retry/fallback information. Important admin, order, webhook, notification, return and refund actions remain traceable for investigation and reconciliation (FR-032 through FR-036).

The existing knowledge flow remains approval-gated: text/URL input may create a draft, and admin approval is required before production use. Source metadata, versioning, deletion propagation, URL restrictions and hostile-input handling remain TBD (AI-008; OPEN-044, OPEN-060).

## 9. Business requirements retained from the discovery baseline

The BRD retains the confirmed requirements in the inventory, including:

- Identity and account controls: FR-001 through FR-006, SEC-001 through SEC-008.
- Catalog/product/variant behavior: FR-007 through FR-011, DATA-001, DATA-003, DATA-007.
- Cart and checkout: FR-012 through FR-021, NFR-001, INT-002.
- Orders and post-purchase: FR-022 through FR-031, SEC-010, INT-006.
- Notifications, administration and audit: FR-032 through FR-036, NFR-002.
- Smart Wardrobe and styling: FR-037, FR-039, AI-003, AI-009 through AI-013, AI-018, DATA-003 through DATA-006.
- AI chat, grounding, explanation, knowledge approval and synchronization evidence: AI-001 through AI-008 and AI-014 through AI-017, with unresolved parts marked TBD.

This list is a traceability index, not permission to fill the unresolved fields in those records.

### 9.1 Major capability traceability

| BRD capability | Source/discovery traceability | Approved baseline traceability |
| --- | --- | --- |
| Women-first AI Personal Stylist + Smart Wardrobe commerce positioning | FCT-002; BUS-001 through BUS-003; DEC-001, DEC-002, DEC-005 | DEC-026, DEC-027 |
| Commerce Core and retained lifecycle | FR-007 through FR-036; BR-008, BR-012 through BR-031; ACT-001 through ACT-004 | DEC-026, DEC-027, DEC-030, DEC-031, DEC-032, DEC-033, DEC-034 |
| Smart Wardrobe V1 | FR-037, FR-039; DATA-006; AI-003, AI-018; DOM-019 | DEC-028 |
| AI Stylist grounding and constraints | AI-003 through AI-005, AI-015 through AI-017; BR-032 through BR-034, BR-042 | DEC-026, DEC-029 |
| V1 exclusions and advisory fit/travel/color limits | FCT-040 through FCT-046; OPEN-045, OPEN-062; DEC-011, DEC-013 through DEC-015 | DEC-027, DEC-028, DEC-029 |

Historical DEC-001 through DEC-025 references in this table retain their original meanings; DEC-026 through DEC-034 are the authoritative V1 baseline records for the newly approved decisions. DEC-030 through DEC-034 refine the commerce behavior used by the PRD without changing the BRD's business positioning or scope boundary.

## 10. Confirmed business constraints

- Backend-calculated totals are authoritative; frontend prices are not authoritative (BR-008, BR-015).
- A real account requires successful OTP verification (BR-001).
- Customer operations enforce ownership of addresses, carts, receipt images and return eligibility (BR-005, SEC-007).
- Online payment confirmation comes from the verified webhook, not the frontend (BR-018).
- Duplicate webhook processing must not repeat payment effects (BR-020).
- Invalid order-state transitions return a business error (BR-021).
- Terminal order states cannot transition onward (BR-022).
- AI purchasable products must be grounded in real StyleMind catalog data (BR-032).
- Body measurements are optional personalization and cannot be mandatory registration fields (BR-033).
- A supplied hard budget must be obeyed (BR-034; DEC-029).
- Knowledge requires approval before production use (BR-035).

## 11. Unresolved and deferred decisions

The following nine decision clusters remain DEFERRED and must not be treated as approved requirements merely because V1 is now defined:

DR-003, DR-004, DR-011, DR-013, DR-014, DR-015, DR-016, DR-017 and DR-018.

DR-005 through DR-009 have approved product policies in DEC-030 through DEC-034. Their residual OPEN/TBD areas include guest-session mechanics, stock mutation/ownership, compensation, webhook protocol, detailed actor/event mapping, COD operations, cancellation/return concurrency, QC/restock, shipment/carrier behavior and payout access controls. The complete inventory remains in `docs/discovery/06_OPEN_QUESTIONS.md`.

Important unresolved areas also include identity lifecycle values, catalog semantics, style/brand/fit semantics, AI retrieval/synchronization, sensitive-data policy, final domain/data ownership, integration guarantees, deployment/operating constraints and visualization details.

The approved resolution of OPEN-047 does not select a model/provider or runtime. The approved resolution of OPEN-048 does not create subjective thresholds. The approved resolution of OPEN-049 does not define every outfit composition edge case. These details remain explicitly TBD/deferred.

## 12. BRD acceptance boundary

This BRD is considered business-complete for the current baseline when a reviewer can verify that:

- The target and V1 boundary match DEC-027.
- Smart Wardrobe behavior matches DEC-028.
- AI promises and Pass/Fail invariants match DEC-029.
- The commerce lifecycle from Product through Order remains present.
- Explicit V1 exclusions are not reintroduced as hidden requirements.
- Unresolved technical/business details are visibly marked TBD/OPEN/DEFERRED.
- No service boundary, API, schema, event or deployment design has been invented.

The next recommended artifact is PRD preparation against this BRD and the approved baseline, after checking any additional business rules that the PRD will need to detail.
