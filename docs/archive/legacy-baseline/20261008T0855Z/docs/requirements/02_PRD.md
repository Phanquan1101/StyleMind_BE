# StyleMind Product Requirements Document

Status: HUMAN-APPROVED

Prepared: 2026-09-21

Approved by: Phan Đoàn Thế Quân — Solo Owner

Approval date: 2026-09-22

Authority: Phan Đoàn Thế Quân — Solo Developer & Product Owner

Parent documents: [Human Decision Baseline V1](../decisions/00_DECISION_BASELINE_V1.md), [BRD](01_BRD.md), [BRD Review](01_BRD_REVIEW.md)

This PRD translates the approved business boundary into product behavior. It does not decide microservice boundaries, databases, APIs, event contracts, deployment, infrastructure, AI providers or implementation code. `TBD`, `OPEN` and `DEFERRED` are intentional product-governance states.

## 1. Product overview

StyleMind is a women-first AI Personal Stylist, Smart Wardrobe and fashion commerce product. It helps female customers with diverse fashion styles move from personal context and owned clothing to explainable outfit recommendations and real purchasable catalog items, while retaining the commerce lifecycle from catalog through post-purchase operations.

The V1 product identity is the combination of:

**Women-first styling + Smart Wardrobe + real Catalog commerce.**

It is not a generic e-commerce site with an unrelated chatbot.

## 2. Product goals

| ID | Goal | Traceability |
| --- | --- | --- |
| PRD-G-001 | Provide a correct commerce path from Product/Variant through Cart, Checkout, Payment and Order. | DEC-026, DEC-027, BUS-001, BUS-003 |
| PRD-G-002 | Help the customer use owned clothing together with real Catalog products in outfit recommendations. | DEC-026, DEC-028, DEC-029, FR-037, AI-003 |
| PRD-G-003 | Prevent purchasable-product hallucination: every item displayed as purchasable maps to a real Catalog Product/Variant. | DEC-026, DEC-029, BR-032 |
| PRD-G-004 | Respect applicable hard constraints while using soft preferences for relevance. | DEC-026, DEC-029, BR-034, BR-042 |
| PRD-G-005 | Preserve customer, admin and operational flows already confirmed in the source baseline. | BUS-003, BUS-004, FR-001 through FR-036 |

## 3. Target users and actors

### PRD-ACTOR-001 — Female customer

The primary V1 customer is female and may have diverse fashion styles. The product does not impose one predefined aesthetic or a fixed age band (DEC-027; BUS-002).

### PRD-ACTOR-002 — Guest

A guest may use a cart without a userId. A guest must authenticate before checkout (ACT-003; FR-012, FR-016).

### PRD-ACTOR-003 — Authenticated customer

An authenticated customer can manage profile/address data, catalog interactions, cart, checkout, orders, post-purchase requests, notifications, Wardrobe and AI Stylist interactions (ACT-001).

### PRD-ACTOR-004 — Admin

An admin manages users, catalog, orders, returns/refunds, notifications, approved AI knowledge and operational reports subject to admin authorization and audit (ACT-002; FR-008, FR-010, FR-033 through FR-036).

## 4. V1 scope

### 4.1 Commerce Core

V1 includes Product/Variant Catalog, Cart, Checkout, Payment and Order. Existing confirmed supporting lifecycle remains applicable: account, profile, address, notifications, order history, cancellation, return, refund, administration, reports and audit.

### 4.2 Smart Wardrobe

V1 includes manual Wardrobe CRUD, personal clothing image upload, basic item metadata, visual/category browsing and AI context integration. Initial category concepts are Tops/Áo, Bottoms/Quần, Dresses/Váy, Shoes/Giày and Accessories/Phụ kiện. These are product concepts, not a final database enum.

### 4.3 AI Stylist

V1 includes outfit recommendation, Wardrobe reuse, Catalog integration, clear owned-versus-Shop distinction, 100% purchasable-product grounding and hard-constraint compliance.

## 5. Explicit V1 exclusions

V1 does not promise or require:

- Exact physical fit or exact size recommendation.
- Automatic advanced body analysis.
- Deep skin-tone analysis or professional Personal Color Analysis.
- Real-time weather integration.
- Automatic multi-day travel outfit planning.
- Automatic packing optimization.
- Sophisticated garment computer vision, complex background extraction, garment segmentation, selfie Wardrobe extraction or automatic clothing recognition from body photos.
- Wear-history tracking, laundry state, Wardrobe analytics or cost-per-wear analytics.

Advanced live 3D remains broader product direction without automatic V1 inclusion. 2D composition, 3D avatar visualization and virtual try-on remain separate concepts. No photorealistic try-on, body scanning or garment physics is implied.

## 6. Product principles

### PRD-PRINCIPLE-001 — Real catalog grounding

If a result is displayed as purchasable, it shall resolve to a valid StyleMind Catalog Product/Variant. An unvalidated result shall not be displayed as currently purchasable (DEC-026, DEC-029; BR-032).

### PRD-PRINCIPLE-002 — Owned versus Shop provenance

The product shall distinguish `YOU ALREADY OWN` from `RECOMMENDED TO BUY`. Owned items come from Wardrobe data. Purchasable items come from Catalog data (DEC-028).

### PRD-PRINCIPLE-003 — Hard versus soft constraints

Applicable hard constraints shall be obeyed. Soft preferences shall influence relevance but shall not be treated as hard acceptance conditions unless explicitly promoted to hard constraints (DEC-029).

### PRD-PRINCIPLE-004 — Advisory fit

Size and fit guidance is advisory only. The customer makes the final size/variant choice before adding an item to the cart (DEC-029).

## 7. User journeys

### 7.1 Discover and purchase

1. Customer or guest browses/searches the catalog.
2. Customer selects a Product/Variant.
3. Guest or authenticated customer adds the Variant to a cart.
4. Guest logs in if necessary; guest cart merges into the user cart according to the approved cart policy.
5. Authenticated customer selects a valid address and starts checkout.
6. System rechecks current Product/Variant, stock and price data.
7. Customer selects COD or SePay/VietQR where available.
8. System creates the order and payment transaction according to the approved commerce policies.
9. Customer follows order state and post-purchase actions.

The approved product outcomes are defined by DEC-030 through DEC-034. Residual session, stock-mechanism, protocol and compensation details remain deferred where explicitly listed in Section 14.

### 7.2 Create a Wardrobe-assisted outfit

1. Customer adds a personal item with approved basic metadata and optional clothing image.
2. Customer browses or updates Wardrobe items.
3. Customer supplies occasion, intended use, style, manually entered general weather/context, budget or restrictions.
4. AI considers owned Wardrobe items and real Catalog products.
5. AI returns an outfit with owned and Shop items clearly separated.
6. Customer may inspect a Shop item and make the final Product/Variant/size choice before cart addition.

### 7.3 Admin operations

1. Admin manages users and catalog data.
2. Admin reviews orders, payments, returns and refunds.
3. Admin reviews notification outcomes and retries failed delivery where supported.
4. Admin reviews approved knowledge sources before production use.
5. Important actions remain available for operational traceability.

### 7.4 AI recommendation to commerce

1. The customer requests an outfit using approved context and constraints.
2. The AI returns owned Wardrobe items and Catalog recommendations with their sources clearly labeled.
3. The customer selects a missing Catalog item recommended for purchase.
4. The selected item resolves to a valid Catalog Product/Variant with Catalog-sourced identity, SKU, brand, price and availability information.
5. The customer makes the final size/variant choice and adds the valid item to Cart.
6. The customer continues through the confirmed Checkout, Payment and Order flow.

If Product/Variant validation fails, the item must not be presented as currently purchasable and must not silently be converted into an owned Wardrobe item.

## 8. Functional requirements

### 8.1 Authentication and profile

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-AUTH-001 | The product shall allow registration through email and a temporary registration flow with OTP verification before creating a real account. | CONFIRMED | FR-001; BR-001, BR-002 |
| PRD-AUTH-002 | The product shall authenticate email/password after account status checks and use the confirmed JWT behavior. | CONFIRMED | FR-002; SEC-003 through SEC-005 |
| PRD-AUTH-003 | The product shall support password reset after OTP verification using the later selected reset alternative. | CONFIRMED; detail TBD | FR-003; OPEN-005 |
| PRD-AUTH-004 | The product shall allow a customer to view/update basic profile data linked to the authenticated user. | CONFIRMED | FR-004 |
| PRD-AUTH-005 | The product shall allow a customer to list, add, update, delete and select a default shipping address for that customer. | CONFIRMED; default policy TBD | FR-005; BR-005, BR-006 |
| PRD-AUTH-006 | The product shall support Vietnam province/ward lookup and address-phone validation/normalization according to a later approved policy. | CONFIRMED; policy TBD | FR-006; INT-004; OPEN-007 |

### 8.2 Catalog and Product/Variant

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-CAT-001 | The product shall expose public categories and support product filtering by keyword, category, price and demographic data. | CONFIRMED | FR-007, FR-009 |
| PRD-CAT-002 | The product shall show Product and Variant details needed for selection, including source-supported size, color, material, SKU, stock, status, price and images. | CONFIRMED; canonical semantics TBD | FR-009, FR-011; DATA-001; OPEN-050 |
| PRD-CAT-003 | Admin shall be able to create/update/delete categories subject to the later approved category hierarchy and deletion policy. | CONFIRMED; policy TBD | FR-008; BR-007 |
| PRD-CAT-004 | Admin shall be able to manage products, statuses, variants and uploaded product images. | CONFIRMED; safeguards TBD | FR-010; BR-007 |
| PRD-CAT-005 | The product shall use backend Product/Variant data as the source for cart validation and checkout price refresh. | CONFIRMED | FR-011, FR-016; BR-008, BR-012, BR-015 |
| PRD-CAT-006 | Product/Variant/SKU meaning, uniqueness, cardinality, activation and deletion behavior shall remain explicitly TBD until DR-004 is resolved. | TBD gate | OPEN-008, OPEN-050 |

### 8.3 Smart Wardrobe

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-WRD-001 | An authenticated customer shall be able to add/create a Wardrobe item. | APPROVED V1 | DEC-028; OPEN-032 |
| PRD-WRD-002 | An authenticated customer shall be able to view a Wardrobe item. | APPROVED V1 | DEC-028; OPEN-032 |
| PRD-WRD-003 | An authenticated customer shall be able to update a Wardrobe item. | APPROVED V1 | DEC-028; OPEN-032 |
| PRD-WRD-004 | An authenticated customer shall be able to delete a Wardrobe item. | APPROVED V1 | DEC-028; OPEN-032 |
| PRD-WRD-005 | The product shall allow a personal clothing image to be uploaded for a Wardrobe item. | APPROVED V1; limits TBD | DEC-028; OPEN-034, SEC-011 |
| PRD-WRD-006 | A Wardrobe item shall support basic metadata for category, primary color and occasion and/or style. | APPROVED V1 | DEC-028; OPEN-033 |
| PRD-WRD-007 | The product shall provide visual Wardrobe browsing through list/grid or an equivalent view and category-based organization. | APPROVED V1 | DEC-028; OPEN-032 |
| PRD-WRD-008 | Wardrobe items shall be available as AI context when the customer requests styling. | APPROVED V1 | DEC-028; FR-037, AI-018 |
| PRD-WRD-009 | The product shall preserve the distinction between an owned Wardrobe item and a purchasable Catalog item. | APPROVED V1 | DEC-028, DEC-029; BR-032 |
| PRD-WRD-010 | V1 shall not require automatic image recognition, segmentation, selfie extraction, wear history, laundry state or Wardrobe analytics. | APPROVED exclusion | DEC-028 |

### 8.4 AI Stylist

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-AI-001 | The product shall allow the customer to provide styling context including occasion, intended use and style. | APPROVED V1 | DEC-029; AI-003, AI-009 |
| PRD-AI-002 | The product may accept general weather/context manually supplied by the customer. | APPROVED V1 | DEC-029; FR-038 |
| PRD-AI-003 | The product shall consider Wardrobe context and prioritize appropriate reuse of owned items where useful. | APPROVED V1 | DEC-028, DEC-029; FR-037, AI-017 |
| PRD-AI-004 | The product shall consider real Catalog products for recommendations intended for purchase. | APPROVED V1 | DEC-026, DEC-029; AI-003, BR-032 |
| PRD-AI-005 | The product shall distinguish `YOU ALREADY OWN` from `RECOMMENDED TO BUY` in the AI result. | APPROVED V1 | DEC-028, DEC-029 |
| PRD-AI-006 | If an AI result is presented as purchasable, it shall map to a valid Catalog Product/Variant; purchasable identity, SKU, brand, price and availability shall come from that Catalog data. | APPROVED hard requirement | DEC-026, DEC-029; BR-032 |
| PRD-AI-007 | If a candidate cannot be validated against the Catalog, the product shall not display it as currently purchasable and shall not invent its product, variant, SKU, brand, price or availability. | APPROVED hard requirement | DEC-029 |
| PRD-AI-008 | The product shall obey applicable hard constraints such as excluded category, prohibited color, excluded product/type and supplied hard maximum budget. | APPROVED hard requirement | DEC-029; BR-034, BR-042 |
| PRD-AI-009 | Hard constraints shall take precedence over aesthetic preferences. | APPROVED hard requirement | DEC-029 |
| PRD-AI-010 | Soft preferences shall be used for relevance but shall not be represented as guaranteed hard compliance. | APPROVED principle | DEC-026, DEC-029 |
| PRD-AI-011 | The AI response shall provide selection reasons and styling tips consistent with the confirmed AI output requirement. | CONFIRMED | AI-005 |
| PRD-AI-012 | Size/fit guidance shall be advisory; the customer shall make the final size/variant decision before cart addition. | APPROVED limitation | DEC-029 |
| PRD-AI-013 | General color coordination may be provided, but the product shall not promise deep skin-tone analysis or professional Personal Color Analysis. | APPROVED limitation | DEC-029; AI-012 |
| PRD-AI-014 | The product shall use scenario-based Pass/Fail acceptance for grounding, owned/Shop distinction and hard-constraint compliance. | APPROVED acceptance | DEC-026, DEC-029; OPEN-048 |
| PRD-AI-015 | The product shall not require a model/provider, exact latency/cost target, subjective quality percentage or arbitrary AI-accuracy threshold in this PRD. | DEFERRED | DEC-029; OPEN-047, OPEN-048 |

### 8.5 Cart

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-CART-001 | A guest shall be able to maintain a cart identified by the approved guest-session mechanism. | CONFIRMED; lifecycle TBD | FR-012; BR-009; OPEN-009 |
| PRD-CART-002 | An authenticated customer shall be able to maintain one active cart in user context. | CONFIRMED | FR-013; BR-009 |
| PRD-CART-003 | The customer shall be able to add, update, remove and clear cart items. | CONFIRMED | FR-014 |
| PRD-CART-004 | Add operations shall validate Product/Variant activity, stock and quantity. | CONFIRMED | FR-014; BR-010 |
| PRD-CART-005 | After authentication, the product shall perform an atomic Guest-to-User Cart merge: duplicate Variant quantities combine; any invalid, inactive, unavailable or over-stock conflict fails the entire merge; price changes do not invalidate merge but must be shown; the Guest Cart clears/retires only after complete success. | APPROVED V1 | FR-015; BR-009 through BR-011; DEC-030; OPEN-010 |
| PRD-CART-006 | A failed cart merge shall leave the Guest Cart available for correction/removal and retry. Partial merge and per-item interactive conflict resolution are out of scope for V1. | APPROVED V1 | FR-015; BR-011, BR-039; DEC-030 |

### 8.6 Checkout and Payment

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-CHK-001 | Checkout shall require authentication, a nonempty cart, a valid address, active Product/Variant data and available stock. | APPROVED V1 | FR-016; BR-012; DEC-031 |
| PRD-CHK-002 | Checkout shall refresh Product/Variant prices from backend data and shall not trust frontend prices. If the price changes before Order creation, the new amount shall be shown and reconfirmed; the changed total shall not be charged silently. | APPROVED V1 | FR-016; BR-008, BR-015; DEC-031, DEC-032 |
| PRD-CHK-003 | Checkout shall create an Order and Order items with purchase-time price and address/recipient snapshot data only after all product-level validations pass. | APPROVED V1 | FR-016; DATA-002; BR-014; DEC-031 |
| PRD-CHK-004 | Repeating a successful checkout with the same user idempotency key shall not create duplicate orders. | CONFIRMED; key policy TBD | FR-016; BR-013; NFR-001 |
| PRD-CHK-005 | Checkout shall be atomic at the product level. If any item fails validity, active status, quantity, current stock or current price validation, no Order is created and no item is silently removed or partially checked out. | APPROVED V1 | FR-016; BR-010, BR-012; DEC-031 |
| PRD-CHK-006 | If Order creation fails, the product shall not present a successful Order; it shall show a recoverable error and allow retry after revalidation. V1 makes no separate promise of a fixed reservation period during ordinary checkout. | APPROVED V1; technical recovery TBD | FR-016; BR-039; DEC-031; OPEN-011, OPEN-014 |
| PRD-PAY-001 | The product shall support a COD transaction flow in which the Order may progress according to the approved lifecycle while Payment remains unpaid/pending; COD becomes PAID only after collection is confirmed. | APPROVED V1 | FR-017; BR-016; DEC-033 |
| PRD-PAY-002 | COD cart clearing and notification failure shall not roll back the main order/payment business effect. | CONFIRMED | FR-017; BR-017; NFR-002 |
| PRD-PAY-003 | The product shall support SePay/VietQR transaction creation and transfer/QR information for a payment-pending Order. A payment attempt expires 15 minutes after its transaction/QR is generated; an eligible Order may receive a new attempt, but the expired attempt is not revived. | APPROVED V1 | FR-018, FR-021; INT-002; DEC-033; OPEN-019 |
| PRD-PAY-004 | Online payment confirmation shall come only from valid authoritative confirmation that matches the intended payment/Order and exact required amount; frontend/user action cannot mark payment PAID. | APPROVED V1 | FR-019; BR-018, BR-019; DEC-033 |
| PRD-PAY-005 | Duplicate payment confirmations shall not repeat payment credit or Order confirmation effects. | CONFIRMED; protocol detail TBD | FR-019; BR-020; DEC-033; OPEN-016 |
| PRD-PAY-006 | V1 money uses whole VND. Backend Product/Variant price, Cart total, Checkout total and Payment amount are authoritative; shipping fee is 0 VND, no separate application tax/VAT line or promotion applies, and frontend must not calculate the authoritative payable amount. | APPROVED V1 | FR-016, FR-019; BR-015; DEC-032 |
| PRD-PAY-007 | Underpayment, overpayment, unmatched payment and late payment shall not mark an Order paid. They require exception/admin handling and shall not revive an expired, cancelled or other terminal Order. | APPROVED V1 | FR-019 through FR-021; BR-019, BR-022; DEC-033 |
| PRD-PAY-008 | Failed COD collection shall not mark Payment paid; the Order follows the applicable failed/cancelled delivery lifecycle. | APPROVED V1; carrier detail TBD | FR-017; BR-016; DEC-033; OPEN-015 |

### 8.7 Orders and post-purchase

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-ORD-001 | The customer shall be able to view own orders, items, purchase prices and current state. | CONFIRMED | FR-022 |
| PRD-ORD-002 | The product shall enforce the explicit source order-state machine and reject invalid transitions with a business error. | CONFIRMED | FR-020; BR-021 |
| PRD-ORD-003 | Terminal states COMPLETED, CANCELLED, EXPIRED and FAILED shall not transition onward. | CONFIRMED | BR-022 |
| PRD-ORD-004 | The customer shall be able to request whole-Order cancellation only in PENDING, PAYMENT_PENDING, PAID or CONFIRMED, with a mandatory reason. Paid/confirmed cancellation requires review; approved paid cancellation creates refund eligibility and rejection includes a reason. | APPROVED V1 | FR-023; BR-023; DEC-034; OPEN-021 |
| PRD-ORD-005 | Admin cancellation shall require an appropriate reason and follow valid state/payment handling. | CONFIRMED; mapping TBD | FR-024; BR-024 |
| PRD-RET-001 | The customer shall be able to submit an item-level, partial-quantity or full return only after the Order is COMPLETED and within 7 calendar days of completion, with Order, Item, quantity, reason and description. Evidence is optional at submission. | APPROVED V1 | FR-026, FR-027; BR-026, BR-027; DEC-034; OPEN-023 |
| PRD-RET-002 | Admin review and QC are required before a physical returned item is finally accepted for refund. Admin rejection shall include a reason. Exchange/replacement is out of scope for V1. | APPROVED V1; detailed QC/restock TBD | FR-028; BR-028; DEC-034; OPEN-024 |
| PRD-RET-003 | The product shall support return shipment information for a suitable return state; detailed shipment states and carrier integration remain TBD. | CONFIRMED; policy overlay applied | FR-029; BR-029; DEC-034; OPEN-025 |
| PRD-REF-001 | The product shall support refund requests and admin complete/fail outcomes. Refund is eligible after approved paid cancellation or approved return with required receipt/QC processing; it is based on actually paid eligible item quantities and cannot exceed the valid paid amount. | APPROVED V1 | FR-030; BR-030; DEC-032, DEC-034; OPEN-026 |
| PRD-REF-002 | A verified refund destination shall remain linked to the corresponding return request. SePay/VietQR refunds use the approved admin process; COD refunds use a verified user-provided bank destination. V1 does not promise fully automatic payout. | APPROVED V1; access/change detail TBD | FR-031; BR-031; DEC-034; OPEN-027 |

### 8.8 Notifications and administration

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-NOT-001 | A customer shall be able to view notifications, unread count/details and mark one or all as read. | CONFIRMED | FR-032 |
| PRD-NOT-002 | Admin shall be able to inspect notification logs, summaries and failed-delivery retry information. | CONFIRMED; triggers/provider/retry policy TBD | FR-033; OPEN-028 |
| PRD-ADM-001 | Admin shall be able to manage users, including role/status actions subject to later guard policy. | CONFIRMED | FR-034; OPEN-029 |
| PRD-ADM-002 | Admin shall have an operational dashboard and reports using later approved metric definitions. | CONFIRMED; formulas TBD | FR-035; OPEN-030 |
| PRD-ADM-003 | Important admin, order, webhook, notification, return and refund actions shall be traceable for investigation and reconciliation. | CONFIRMED; retention/detail TBD | FR-036; BUS-004; OPEN-031 |
| PRD-ADM-004 | Text/URL fashion knowledge shall pass through draft and admin approval before production use. | CONFIRMED; metadata/security TBD | AI-008; BR-035, BR-036 |

## 9. User-facing states

The product shall communicate at least the following user-facing states where the relevant flow applies:

- Authentication: registration pending verification, verified account, invalid/expired OTP, invalid credentials and account status restriction.
- Catalog: results, no results, unavailable/inactive Product or Variant and unavailable stock.
- Cart: empty cart, valid item, invalid/inactive item, quantity/stock conflict, atomic merge success or conflict, price-change notice and retry result.
- Checkout: ready, validation failure, changed-price reconfirmation, recoverable creation failure, payment pending, payment confirmed, expired and failed according to the approved state/policy.
- Order: the ten source-defined order states, cancellation eligibility/review result and invalid-transition error behavior remain authoritative; display/history detail is partly TBD.
- Payment: authoritative confirmation, 15-minute payment-attempt expiry, new-attempt option, abnormal-payment exception and terminal-order protection.
- Return/refund: completed-order seven-day window, request submitted, under review, rejected, QC outcome, refund pending/processing/complete/failed and verified-destination/manual handling according to approved policies.
- Wardrobe: empty Wardrobe, item saved, item edited, item deleted, image upload validation failure and unavailable image.
- AI: usable recommendation, no suitable result, hard-constraint conflict, unvalidated product suppressed and service/knowledge unavailable.

Exact copy, transitions, timeout values, retry behavior and exposure of state history remain TBD where the source does not define them.

## 10. Error, empty and unavailable states

| ID | Requirement | Status |
| --- | --- | --- |
| PRD-STATE-001 | The product shall not show an unvalidated item as currently purchasable. | APPROVED V1 |
| PRD-STATE-002 | The product shall expose a business error for an invalid order-state transition. | CONFIRMED |
| PRD-STATE-003 | Empty catalog, empty cart, empty Wardrobe and no-suitable-outfit states shall be represented without inventing a fallback purchase item. | Product requirement; exact copy TBD |
| PRD-STATE-004 | If an owned Wardrobe item or Catalog product is unavailable to the relevant flow, the product shall not mislabel its provenance and shall communicate the unavailable result according to later UX detail. | APPROVED principle; detailed behavior TBD |
| PRD-STATE-005 | Ancillary notification failure shall not roll back the main order/payment business effect where the confirmed rule applies. | CONFIRMED |
| PRD-STATE-006 | Payment mismatch, duplicate confirmation, abnormal/late payment and invalid ownership attempts shall be rejected or handled according to DEC-033 and the remaining detailed policies. | APPROVED V1; protocol detail TBD |

## 11. Accessibility and visual quality

| ID | Requirement | Status | Traceability |
| --- | --- | --- | --- |
| PRD-UX-001 | The product shall provide strong visual outfit presentation consistent with the confirmed product direction. | CONFIRMED; detailed acceptance TBD | UX-001; historical DEC-012 |
| PRD-UX-002 | Visual output shall preserve the readable distinction between owned items and Shop recommendations. | APPROVED V1 | DEC-028, DEC-029 |
| PRD-UX-003 | Measurable accessibility, supported devices/browsers and visual-quality acceptance criteria shall be defined before final PRD acceptance or implementation. | TBD gate | OPEN-061; DR-017 |

No specific accessibility standard, device matrix or contrast/interaction threshold is invented in this draft.

## 12. Acceptance criteria

### 12.1 Commerce acceptance

- A real Product and selected Variant can be discovered and represented in the catalog.
- A valid Variant can be placed in a guest or authenticated cart according to the confirmed cart behavior.
- A Guest-to-User merge either succeeds completely or fails without changing the User Cart; duplicate quantities, conflicts, price-change notice, Guest Cart retention and retry follow DEC-030.
- Checkout requires login, valid address, nonempty cart, current price and valid stock checks.
- Any failed product-level checkout validation prevents Order creation and partial checkout; a changed total requires reconfirmation.
- Backend totals and purchase-time snapshots are used.
- Money is whole VND with zero V1 shipping fee, no separate application tax/VAT line and no V1 promotions; backend amount is authoritative.
- COD is not financially PAID until collection is confirmed; SePay/VietQR requires valid exact-match authoritative confirmation, expires after 15 minutes per attempt, and abnormal/late money never revives a terminal Order.
- Duplicate successful checkout/payment webhook effects are prevented according to the confirmed rules.
- The explicit order state machine rejects invalid transitions and protects terminal states.
- Whole-order cancellation, Completed-only seven-day returns, admin review/QC, no exchange/replacement and paid-amount refund caps follow DEC-034.
- Customer/admin ownership and role protections apply to the confirmed surfaces.

### 12.2 Smart Wardrobe acceptance

- An authenticated customer can add, view, update and delete a Wardrobe item.
- A personal clothing image can be uploaded subject to later approved limits.
- Basic metadata can be stored and displayed.
- Items can be browsed visually and by category.
- A saved Wardrobe item is available as AI context.
- The output distinguishes `YOU ALREADY OWN` from `RECOMMENDED TO BUY`.
- V1 does not require automatic recognition, segmentation, selfie extraction, wear/laundry tracking or analytics.

### 12.3 AI acceptance

- Every displayed purchasable item maps to a real Catalog Product/Variant.
- Any invalid or stale/unvalidated candidate is not shown as currently purchasable.
- Owned Wardrobe and Shop items are correctly distinguished.
- Applicable hard constraints are respected.
- Hard-constraint violation, invalid mapping and incorrect provenance are mandatory FAIL outcomes.
- Size/fit is presented as advisory, not guaranteed.
- General color guidance does not claim Personal Color Analysis.
- Manual general weather/context can be used without requiring live weather integration.
- Subjective taste/relevance thresholds remain TBD and are not silently invented.

## 13. Traceability

| PRD area | BRD/discovery traceability | Approved decisions |
| --- | --- | --- |
| Product goals and V1 boundary | BUS-001 through BUS-004; FCT-002, FCT-004, FCT-038 | DEC-026, DEC-027 |
| Authentication/profile/address | FR-001 through FR-006; BR-001 through BR-006; SEC-001 through SEC-008 | Existing confirmed baseline; DR-003 details deferred |
| Catalog and commerce | FR-007 through FR-036; BR-007 through BR-031; NFR-001, NFR-002 | DEC-026, DEC-027, DEC-030 through DEC-034; DR-004 residual semantics and technical subdetails remain deferred |
| Smart Wardrobe | FR-037, FR-039; DATA-006; AI-003, AI-018; DOM-019 | DEC-028 |
| AI Stylist | AI-003 through AI-005, AI-009 through AI-017; BR-032 through BR-034, BR-042 | DEC-026, DEC-029 |
| Notifications/admin/audit | FR-032 through FR-036; BUS-004; OPEN-028, OPEN-030, OPEN-031 | DEC-026; DR-018 deferred |
| UX/accessibility/travel/visual | UX-001 through UX-003; FCT-043 through FCT-046; OPEN-045, OPEN-061, OPEN-062 | DEC-027, DEC-029; DR-014 and DR-017 deferred |

All PRD IDs are additive product-document identifiers. Existing BUS/FR/BR/AI/DATA/SEC/UX/OPEN/DR/DEC identifiers remain unchanged.

## 14. Open and deferred items

The following clusters remain deferred and are not resolved by this PRD:

- DR-003: identity/authorization lifecycle details.
- DR-004: catalog semantics, snapshots and history.
- DR-011: style/brand/fit/body/color semantics and AI responsibility.
- DR-013: AI retrieval, knowledge authority and synchronization.
- DR-014: broader travel/visual/3D experience boundary.
- DR-015: sensitive data, conversation memory and AI trust controls.
- DR-016: final context/data ownership and integration architecture.
- DR-017: measurable quality, accessibility, operating and recovery targets.
- DR-018: operational evidence, notifications and report semantics.

The five former PRD-finalization clusters are now governed by DEC-030 through DEC-034. The following residual subdetails remain TBD without blocking PRD readiness: OPEN-009, OPEN-011, OPEN-014, OPEN-015, OPEN-016, OPEN-018, OPEN-019, OPEN-021, OPEN-023, OPEN-024, OPEN-025 and OPEN-027. These include session mechanics, stock ownership/reservation, detailed compensation, webhook protocol, exact actor/event mapping, COD operations, request/concurrency details, QC/restock, shipment/carrier behavior and payout access controls.

The PRD records deferred items as TBD/DEFERRED and does not select providers, topology, services, schemas, APIs, events or implementation mechanisms.

## 15. PRD finalization gates

The five minimum product/business policies that previously blocked PRD finalization are now approved and incorporated:

- DR-005 → DEC-030: atomic cart merge.
- DR-006 → DEC-031: atomic product-level checkout.
- DR-007 → DEC-032: VND-only minimal money policy.
- DR-008 → DEC-033: strict authoritative payment confirmation.
- DR-009 → DEC-034: conservative cancellation/return/refund policy.

Residual TBD details are explicitly recorded in Section 14 and the OPEN-question overlay. They are not blockers to this PRD readiness decision because they do not alter the approved V1 product outcomes. Identity token mechanics, service boundaries, database ownership, API/event style, deployment, AI provider/runtime, knowledge synchronization and detailed security controls remain deferred to later artifacts.

See [PRD finalization decisions](../decision-review/06_PRD_FINALIZATION_DECISIONS.md) for the recorded human approval and decision-to-DEC mapping.

## 16. PRD status

PRD STATUS: HUMAN-APPROVED

The PRD is HUMAN-APPROVED by Phan Đoàn Thế Quân — Solo Owner on 2026-09-22. A later Domain Model, SAD, security/AI specification and implementation workflow must use only approved requirements and decisions.
