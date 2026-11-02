# StyleMind — UI Revision and Physical-Model Readiness

Status: WORKING REVIEW — does not supersede the approved PRD, Domain Model, SAD or decision records.

Review date: 2026-09-29

## 1. Review basis

The reviewed prototype is the Figma Make React project at `C:\Users\FPTSHOP\Desktop\Untitled`. The active screens are `src/screens/Profile.tsx`, `Wardrobe.tsx`, `AIStylest.tsx`, `Shop.tsx`, `ProductDetail.tsx`, `Checkout.tsx`, and `OrderTracking.tsx`.

The prototype is evidence of intended screen content, not evidence of persisted backend behavior. For example, profile save actions only display transient feedback, and the stylist/wardrobe interactions are client-side demo state. This review does not edit that project or revise any approved StyleMind baseline document.

Authority used for the existing V1 baseline:

- `docs/requirements/02_PRD.md`
- `docs/domain/01_DOMAIN_MODEL.md`
- `docs/architecture/01_SAD.md`
- `docs/decisions/DEC-035.md` and `DEC-036.md`

The user's report of later leader-approved requirements (multiple branches; shareable wardrobes with private/protected/public visibility; friend chat; recommendations limited to StyleMind shop variants; product imagery primarily attached to variants) is recorded below as a newer human input that still needs reconciliation into the formal baseline. It is not silently treated as if those approved documents already contained it.

## 2. Revision matrix

| Review topic | Evidence in current prototype | Existing approved baseline / newer human input | LDM disposition | Revision result |
| --- | --- | --- | --- | --- |
| Chat Session: audience and context | `AIStylest.tsx` is a customer-to-AI stylist chat. The active screen set has no friend-chat page, participant list, group controls, or wardrobe-sharing/pinning control. | The approved Domain Model confirms stored user-context Chat Sessions and Messages, but does not define friend-to-friend chat. The user reports a later leader approval for chatting with friends and sharing a wardrobe with a friend. | Keep Chat Session and Message. Add the friend-chat and wardrobe-context concepts to the working LDM only as requirements from the newer human input. Whether a session is 1:1 or group, how participants are represented, and whether/how a wardrobe is attached to a session remain TBD. | Prototype gap; not grounds to remove the human-approved capability. Add/ revise screens and flows before final schema. |
| Wardrobe Requirement vs Style Profile | `Profile.tsx` has style-aesthetic selections, fit silhouette, optional body/size fields, favorite brands, color preferences and a Save button. `Wardrobe.tsx` supports owned-item browsing/addition. Stylist input accepts a per-request free-text prompt. There is no requirement editor attached to a Wardrobe. | The Domain Model treats Styling Request as a derived candidate, not a confirmed persisted entity; Wardrobe metadata is basic category/color/occasion and/or style. | Model shown saved preferences as customer-level style/fit preferences, not a persistent per-Wardrobe `Wardrobe_Requirement` table. Treat occasion/request-specific input as request/session context unless a separate decision requires persistence. Optional body/fit data needs explicit privacy and lifecycle treatment. | The UI supports a customer-level preference profile concept, but its React state is not proof of backend persistence. No evidence supports a persistent Wardrobe Requirement entity. |
| Branch stock | Shop/product screens show product, color and size availability but no branch selector, branch-specific stock, pickup branch or stock-by-location view. Checkout does not select a branch. | The user reports leader approval for multiple branches. Existing PRD/Domain Model do not yet define branch inventory or reservation mechanics. PRD-CHK-006 explicitly makes no promise of a fixed reservation period during ordinary checkout. PRD-PAY-003 defines a 15-minute online payment-attempt expiry. | Add Branch and branch-specific Variant stock to the working logical scope only after the new branch requirement is reconciled into the baseline. Keep reservation/hold quantity, expiry and release rules TBD. A 15-minute payment-attempt expiry must not be interpreted as a 15-minute stock hold. | Prototype gap. The sample prompt's proposed 15-minute stock hold is not an approved rule and is not adopted here. |
| Wardrobe visibility and sharing | `Wardrobe.tsx` contains no Private/Protected/Public controls, share action, recipient selection or access-management UI. | The user reports leader approval for private/protected/public visibility and sharing with friends. Those details are not present in the approved PRD/Domain Model. | Retain Wardrobe sharing/access concepts in the working LDM. The meaning of Protected, public-read scope, recipient revocation, and whether one customer can own multiple wardrobes remain TBD. | Prototype gap; do not omit the requirement merely because the current screen lacks it. |
| Product image ownership | `ProductDetail.tsx` renders `product.images[]` while color and size are selected separately. The current UI data flow does not bind the gallery explicitly to the selected Product Variant. | The newer human input says product imagery is primarily associated with a Variant. The current Domain Model describes Product images and Variant information without settling canonical image ownership. | Use Variant Image as the working direction for purchasable option imagery, but preserve any genuinely product-level marketing image only if the human baseline allows both. | Prototype/data-shape mismatch. Variant-specific image galleries and color/size changes need to be represented explicitly. |
| Payment and refund transaction | `Checkout.tsx` presents VietQR and COD. It uses a demo 10-minute countdown and simulates a webhook after seven seconds; this is not a real payment flow. | The approved PRD allows a new online payment attempt for an eligible Order after an earlier attempt expires; COD remains pending until collection is confirmed. Refund is a separate eligible post-purchase process. The user's earlier conceptual diagram depicts Order–Transaction as 1:1 and a shared payment/refund Transaction. | Do not implement a single 1:1 Order–Transaction row as the full V1 payment/refund model without a formal decision revising PRD semantics. The approved baseline requires representing payment attempts separately from Order lifecycle and refund outcomes. | Material model conflict. The demo timer (10 minutes) is also inconsistent with the approved 15-minute payment-attempt expiry; neither timer defines inventory reservation. |
| Order returns/exchanges | `OrderTracking.tsx` offers “Exchange / Return”, promises 14 days and a 48-hour courier pickup, and includes exchange reasons. | PRD-RET-001/002 approve item/quantity returns after completion within seven calendar days, admin review and QC, and explicitly exclude exchange/replacement in V1. Return shipment details remain TBD. | Keep Return Request and its item/quantity relationship. Do not add Exchange/Replacement or hard-code 14 days/48 hours into LDM/DDL under the current approved baseline. | Direct UI–PRD conflict. Revise the screen/flow or obtain an explicit human decision to change V1 policy. |

## 3. Physical-model constraints already established

1. PostgreSQL is approved for authoritative transactional storage (DEC-035/036).
2. DEC-036 approves seven initial independently runnable applications: identity, catalog, cart, checkout, payment, wardrobe and stylist. It does not silently authorize a new Social service/database or permit cross-service database mutation.
3. Existing service/database ownership must follow the approved boundary and data-owner mapping. Chat-with-friends and wardrobe-sharing ownership is not assigned by this review.
4. PostgreSQL UUID/type/index choices are physical design decisions; they cannot resolve the business conflicts above.
5. Do not make a database-per-service DDL package appear complete while Social ownership, branch-stock rules and the payment/refund mapping remain unapproved or unrecorded.

## 4. Check-loop result

- Docker Engine is unavailable in the current environment (`docker info` could not reach the server).
- Local PostgreSQL 18 responds on `127.0.0.1:5432`, but a non-interactive read-only connection as `postgres` failed because no password was supplied.
- No SQL was executed and no database was created or modified. DDL compilation/integration is therefore NOT VERIFIED.

## 5. Human decisions needed before final DDL

These are material model/authority questions, not routine physical tuning:

1. **Payment baseline:** Keep the approved PRD model (Order may have multiple payment attempts; refund remains a separate process/outcome), or formally revise PRD/Domain Model to make the conceptual 1:1 Transaction model authoritative. Recommendation: retain the already approved PRD semantics unless the owner explicitly changes them.
2. **Social data owner:** Assign friend relationships, friend chat and wardrobe-sharing records to an existing approved application boundary, or explicitly approve a new Social boundary/database. Recommendation: do not invent or assign a new service boundary in the DDL.
3. **Branch stock policy:** Confirm the branch-specific stock data and user-facing fulfillment behavior. Keep reservation/hold quantity and TTL unresolved unless explicitly approved; the QR payment expiry alone does not establish a hold.
4. **Visibility/chat semantics:** Define what Protected means and whether friend chat is 1:1 only or supports groups. The current prototype cannot answer these questions.

Until these are reconciled, safe work is limited to a clearly labeled draft/partial physical model for the existing approved domains; final complete DDL and a successful database check cannot be claimed.
