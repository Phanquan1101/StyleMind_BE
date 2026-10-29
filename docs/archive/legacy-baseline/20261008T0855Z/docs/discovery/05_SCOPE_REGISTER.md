# StyleMind Scope Register

Scope and evidence status are separate dimensions. A capability can be CONFIRMED in overall scope while its release placement is TBD. Source references use the register in file 01; requirement IDs use file 02. No first implementation milestone, feature cutoff or delivery date has been approved in the supplied sources.

## A. EXISTING CORE CAPABILITIES — INITIAL MILESTONE TBD

The original commerce and AI Stylist capabilities are the existing business core. Calling them core is a DERIVED analytical grouping from S01 and S03 section 1, not an INITIAL release commitment. Exact milestone membership is TBD under OPEN-003.

| Capability | Evidence status | Scope basis and boundary |
| --- | --- | --- |
| Account, OTP login/reset, profile and addresses | CONFIRMED | FR-001 through FR-006; S01 sections 2-3. Lifecycle/security parameters remain open. |
| Categories, catalog search, products, variants, stock checks, admin images | CONFIRMED | FR-007 through FR-011; S01 section 4. Inventory mutation/reservation is TBD, not assumed complete. |
| Guest/user cart, item operations and login merge | CONFIRMED | FR-012 through FR-015; S01 section 5. Merge failure and quantity policy remain open. |
| Checkout, purchase snapshots, backend totals and deduplication | CONFIRMED | FR-016; BR-008, BR-012 through BR-015. No totals formula or distributed transaction design supplied. |
| COD and SePay/VietQR with reconciliation | CONFIRMED | FR-017 through FR-019; S01 section 7. COD collection and abnormal online payments remain open. |
| Order state machine, expiry, customer history and receipt photos | CONFIRMED | FR-020 through FR-022, FR-025; S01 sections 8, 10, 19. Triggers and image limits remain open. |
| Cancellation, return/QC/shipment, refund and payout data | CONFIRMED | FR-023 through FR-031; S01 sections 9, 11-12. Conditional rules and incomplete state machines remain TBD. |
| Notifications, admin operations, reporting and audit | CONFIRMED | FR-032 through FR-036; S01 sections 13-14, 18. Exact metrics, delivery and retention policy remain open. |
| AI chat, grounded catalog outfits, budget and explanation | CONFIRMED | AI-001 through AI-006; S01 sections 15.1-15.2; S03 section 11. Existing pipeline is subject to audit, not a final technical selection. |
| AI catalog synchronization and approved knowledge ingestion | CONFIRMED as source-described capabilities | AI-007, AI-008; S01 sections 15.3-15.4. Conditional sync details, graph/vector responsibilities and failure handling remain open. |

## B. ADVANCED / LATER MILESTONE

| Capability | Evidence status | Scope placement |
| --- | --- | --- |
| Advanced live 3D visualization | CONFIRMED in broader scope; milestone TBD | UX-002; S03 section 8 explicitly includes it and prevents automatic first-milestone inclusion. Advanced is the source's characterization. A particular later milestone is not selected here. OPEN-062. |

Smart Wardrobe, travel and personalization are not automatically assigned later simply because they are newer. Their milestone placement is unresolved.

## C. OVERALL PROJECT SCOPE

| Capability or constraint | Evidence status | Why included and what remains unknown |
| --- | --- | --- |
| Women-first specialization | CONFIRMED | BUS-002; S03 section 2. No age range, exclusive market or style taxonomy inferred. Vietnam address behavior is explicit but does not prove exclusive geographic positioning. |
| Continued e-commerce lifecycle | CONFIRMED | BUS-003; S03 section 1 preserves the original commerce direction. |
| Owned-clothes and catalog-based Smart Wardrobe styling | CONFIRMED direction | FR-037; S03 section 3. Item capture, fields, images, history, favorites and ownership remain TBD. |
| Brand information and appropriate brand preference | CONFIRMED direction | DATA-003, AI-010; S03 section 4. No independent brand service, management workflow or mandatory brand filter inferred. |
| Personal style, size/fit and optional measurements | CONFIRMED direction | AI-009, AI-011, AI-013; S03 sections 2, 5. Algorithms, units and exact optional field set remain TBD; BR-033 applies. |
| Color coordination | CONFIRMED direction | AI-012; S03 section 6. Color ownership and matching logic are explicitly unresolved. |
| Travel and trip-duration-aware planning | CONFIRMED direction | FR-038; S02 Product Focus; S05 section 2. Detailed destination/activity/occasion/reuse and shopping behavior remain TBD. |
| Strong visuals and advanced live 3D | CONFIRMED direction | UX-001, UX-002; S03 section 8. 2D, avatar 3D and virtual try-on have separate unresolved meanings. |
| Java/Spring Boot, microservices, React and specification-led agent development | CONFIRMED constraints | ARCH-001 through ARCH-004, ARCH-008; S03 sections 9-13. Final topology and supporting libraries remain TBD. |

## D. PROPOSED

No additional product feature is proposed in this discovery baseline. The only recommendations are documentation/review sequencing in file 12 and explicitly marked canonical terminology candidates in file 08. Both require human review; neither adds product scope or a service boundary.

## E. OUT OF SCOPE

For this Phase 0 task, implementation code, schemas/migrations, generated services/apps, final BRD/PRD/SAD, final API contracts, final architecture and implementation plans are explicitly out of scope (S05 opening restrictions; S06). They have not been authored.

The available sources do not permanently exclude an entire product capability merely because it is advanced or undecided. Computer vision, body scanning, segmentation, garment physics, diffusion try-on and photorealistic virtual try-on are unapproved techniques requiring separate specification/approval (S02 Interpretation Constraint). They are not confirmed requirements and are not silently added through UX-002.

## F. UNRESOLVED

| Item | Current classification | Review path |
| --- | --- | --- |
| Initial milestone and acceptance boundary, including Smart Wardrobe/AI depth | TBD | OPEN-003 |
| Conditional commerce policies: categories, public activity, price display, VAT, cancellation review and refund eligibility | TBD | OPEN-008, OPEN-012, OPEN-021, OPEN-026 |
| Stock reservation/mutation/restock and failure consistency | TBD | OPEN-011, OPEN-014, OPEN-024 |
| Wardrobe capture, images, brand/size/color/category metadata, purchase source, favorites/history | TBD | OPEN-032 through OPEN-034 |
| Style/body profile collection, units, validation and brand sizing | TBD | OPEN-035 through OPEN-038 |
| Preference memory, AI pipeline choices, sync, evaluation and constraint priority | TBD | OPEN-039 through OPEN-044, OPEN-047 through OPEN-049 |
| Travel destination, activities, occasion, reuse and day-by-day planning | TBD detailed behavior within confirmed direction | OPEN-045 |
| Weather integration and packing optimization | TBD approval; not automatically confirmed | OPEN-045; S03 section 7 |
| Carrier API, automated bank payout, new target groups, extra roles or new services | TBD; no adopted requirement | INT-006; OPEN-002, OPEN-025, OPEN-026, OPEN-052 |
| Meaning, milestone and acceptance of 2D composition, avatar 3D and virtual try-on | TBD | OPEN-062 |

There is no approved scope cutoff to infer from the presence of a section in the original description. Later specifications must preserve the distinction between confirmed scope, optional source behavior and milestone decisions.
