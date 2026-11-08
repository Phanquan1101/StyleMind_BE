# EP-07 P03-B — request intake contract

**Status: P03-B1 APPROVED; P03 remains PARTIAL.** On 2026-11-04 the owner approved this proposal's shop/manual request intake and read-back slice, including its exact API behavior and a new V2 containing only `styling_request` and `styling_constraint`. This supersedes Prompt 10's planning-only boundary solely for P03-B1. It does not approve owned context, generation, provider/LLM, P04.3, persistent-database migration, other services, or full P03/US-07.01/EP-07 completion. The approved scope and test evidence are recorded separately in the execution manifest.

## 1. Current evidence and boundary

- P03-A source currently exposes `POST /api/v1/stylist/sessions` and `GET /api/v1/stylist/sessions/{session_id}/messages`. The Gateway entry is `/stylist/...`; its route strips `/stylist` before forwarding. The controller derives the owner from validated JWT `sub`, returns direct DTOs and `Cache-Control: no-store`. Both CUSTOMER and ADMIN routes are self-only; session lookup is owner-scoped, so missing and foreign sessions share 404.
- Stylist V1 contains only `stylist_session` and `stylist_message`, with a local message-to-session FK, no cross-service FK, and a stable `(session_id, sent_at DESC, message_id DESC)` history index. V1 must remain immutable.
- The current parser rejects duplicate JSON keys, unknown fields and trailing JSON tokens; the endpoint converter caps request bodies at 32 KiB. History uses a bounded `limit + 1` keyset query, binds its opaque cursor to owner and session, orders by timestamp plus message UUID, and has a regression test for fixed query count.
- The P03-A handover records `mvn -B -ntp -f stylist-service/pom.xml verify`: 83 tests, 0 failed, 0 errors, 0 skipped, using PostgreSQL 18 Testcontainers. That is inherited handover evidence, not a command run in this Prompt 10 turn. Its MockMvc tokens are test-signed. No Identity-issued-token/Postman/Newman run through Gateway is recorded.
- Current Identity source issues RS256 JWTs with configured issuer/audience, UUID `sub`/`jti`, `iat`, `exp`, `roles`, and `kid`; defaults are `https://identity.stylemind.local` and `stylemind-api`. Stylist checks RS256, issuer/audience, timestamps, UUID subject/JTI and CUSTOMER/ADMIN roles. The current profile endpoint is self-scoped at `GET /api/v1/me/style-profile`; details are in §6.
- Commerce currently owns catalog identity, descriptions, variant prices/status, and image metadata. Wardrobe currently owns saved shop-variant outfits and shares; it does not expose a confirmed-owned-garment contract. See §7.

The request-intake behavior in §§2–5 and the P03-B1 scope in §8 are approved only for the slice named above. Sections discussing future consumers, owned context, generation, catalog readiness, and remaining EP-07 work remain proposals or source findings as labeled. P03-B1 uses only the Stylist database and disposable integration-test databases; it makes no remote service calls.

## 2. Proposed user flow

1. The customer creates a session through the existing P03-A endpoint. The returned `session_id` identifies the Stylist-owned conversation.
2. The customer submits a request to that session with a valid access JWT. Stylist validates the JWT, resolves the session by `(session_id, JWT sub)`, checks that it is open, validates the input and, if supplied, verifies `source_message_id` is a USER message in that same session.
3. In one short Stylist database transaction, Stylist optionally creates the new USER message, creates the request, and persists the complete constraint set. The request begins in `RECEIVED` state. A failed validation or failed insert leaves no new message, request, or constraint rows.
4. The API returns `201 Created` for the stored request resource. It does not say that a recommendation was generated, queued, or will eventually be generated. A submitted message appears in existing session history; a structured-only request does not create an artificial history message.
5. The customer can read the request by its ID and read the session's messages using the existing history endpoint. The POST response provides the request ID; no new history wrapper or synthetic ASSISTANT message is proposed.
6. Generation begins only in a later, separately approved P07 execution path: an actual executor accepts a `RECEIVED` request and commits the agreed transition into a generation state. No such executor or transition exists today. Until then, the request remains `RECEIVED`; no `PROCESSING`, `SUCCEEDED`, `NO_RESULT`, fake outfit, or fake assistant reply is returned.

## 3. Proposed HTTP contract

The two request routes and details below are approved for P03-B1. Success is a direct JSON DTO; errors use Problem Details; all user-specific responses use `Cache-Control: no-store`.

| Operation | Service-local path | Through current Gateway | Purpose |
|---|---|---|---|
| Existing create session | `POST /api/v1/stylist/sessions` | `POST /stylist/api/v1/stylist/sessions` | P03-A; unchanged |
| Existing read history | `GET /api/v1/stylist/sessions/{session_id}/messages` | `GET /stylist/api/v1/stylist/sessions/{session_id}/messages` | P03-A; unchanged |
| Proposed submit request | `POST /api/v1/stylist/sessions/{session_id}/requests` | `POST /stylist/api/v1/stylist/sessions/{session_id}/requests` | Persist an owner-scoped request only |
| Proposed read request | `GET /api/v1/stylist/requests/{request_id}` | `GET /stylist/api/v1/stylist/requests/{request_id}` | Read the caller's stored request; no generation or retry |

### 3.1 Request fields and JSON example

```json
{
  "message": "Tôi cần một bộ đi làm gọn gàng.",
  "source_message_id": null,
  "occasion": "đi làm",
  "intended_use": null,
  "style_context": "tối giản",
  "general_weather_context": null,
  "max_budget_vnd": 850000,
  "budget_is_hard": true,
  "constraints": [
    {
      "constraint_kind": "EXCLUDE_COLOR",
      "catalog_entity_id": null,
      "text_value": "RED",
      "is_hard": true
    }
  ]
}
```

The approved P03-B1 field rules are:

| Field | Omitted / null / default | Proposed validation and meaning |
|---|---|---|
| `message` | Omitted or null means there is no new USER message | If present, Unicode-trim, nonblank, at most 2,000 code points, no control characters. `message` and `source_message_id` are mutually exclusive. A supplied message is persisted as USER and becomes the request's `source_message_id`. |
| `source_message_id` | Omitted or null means no prior message is referenced | If present, require a canonical UUID and an owner-scoped USER message in this exact session; foreign/missing/non-USER references return the same 404. Do not copy message text into a second row. |
| `occasion`, `intended_use`, `style_context` | Omitted or null means unset; never inherited from an earlier request | Optional trimmed nonblank text, each at most 128 code points; control characters rejected. They are request context, not category/slot codes. |
| `general_weather_context` | Omitted or null means unset; no weather lookup or inference | Optional manually supplied trimmed text, at most 1,000 code points. |
| `max_budget_vnd` | Omitted or null means no budget | Exact JSON integer from 0 through `Long.MAX_VALUE`; reject fractional, exponent-form, string, negative or overflow values. It is a per-outfit budget in whole VND. |
| `budget_is_hard` | Omitted defaults to `false`; explicit null is invalid | `true` requires a non-null amount and is inclusive at equality. `false` with an amount is a soft ranking target and never filters an otherwise valid outfit. No tax, shipping, or discount is included. |
| `constraints` | Omitted defaults to `[]`; explicit null is invalid | Full set for this request, at most 20 entries. Empty means no exclusions. Do not union or inherit prior-request constraints. Reject duplicate canonical `(kind,value)` entries. |
| `constraints[].constraint_kind` | Required | Supported values: `EXCLUDE_CATEGORY`, `EXCLUDE_COLOR`, `EXCLUDE_PRODUCT`, `EXCLUDE_VARIANT`. The public DTO rejects unknown kinds for both hard and soft entries. The P02 engine keeps its internal unknown-soft ignore-with-warning behavior; unknown color values under a known kind still follow the P02 hard-reject/soft-warning rule when generation later evaluates them. |
| `constraints[].catalog_entity_id` / `text_value` | Exactly one non-null value; the other may be omitted or null | CATEGORY/PRODUCT/VARIANT use a UUID in `catalog_entity_id`; COLOR uses a nonblank code in `text_value` (maximum 64 code points). They are logical Commerce references, never cross-service FKs. A category name is not accepted as an ID, and AI must not infer slot mappings. |

At least one of a nonblank `message`, a valid `source_message_id`, or one nonblank context text field is required. A budget or exclusion list by itself is not a styling need. Message/context text is not interpreted as a hard budget or hard exclusion. The one-outfit rule remains exactly one women's TOP plus one BOTTOM; other slots stay outside scope.

The first request-intake slice does **not** accept `wardrobe_id`, `requirement_wardrobe_id`, `wardrobe_item_ids`, `customer_id`, `owner_id`, role, current price, product metadata, `request_status`, server timestamps, generation status, provider output, or an idempotency key. Unknown fields fail validation. Owned input is added only after Wardrobe publishes a trusted producer contract; a saved outfit, `variant_id`, `outfit_item_id`, `creation_source`, or client flag cannot prove ownership.

Use a strict JSON object parser: reject malformed/non-object JSON, duplicate keys, unknown keys, wrong types and trailing root tokens. Proposed body limit is 32 KiB; JSON media type only. GET has no body and does not mutate state.

### 3.2 Success and error responses

Proposed POST response:

```json
{
  "request_id": "90000000-0000-4000-8000-000000000091",
  "session_id": "90000000-0000-4000-8000-000000000090",
  "source_message_id": "90000000-0000-4000-8000-000000000092",
  "request_status": "RECEIVED",
  "requested_at": "2026-10-31T08:00:00Z",
  "generation_started": false
}
```

Return `201 Created`, `Location: /stylist/api/v1/stylist/requests/{request_id}`, and `Cache-Control: no-store`. `request_id` and timestamps are server-generated. `source_message_id` is null for a structured-only request. `generation_started=false` and `RECEIVED` mean only “persisted”; there is no `202 Accepted` because no queue or executor exists.

`GET /api/v1/stylist/requests/{request_id}` returns `200` with the same IDs/status/time plus persisted context text, budget, and constraints. It does not copy message content; callers read a linked message through the existing history API using `source_message_id`. It returns no recommendation fields. It is read-only and does not retry work. Missing and foreign requests both return `404 RESOURCE_NOT_FOUND` by joining through the owner session. The existing messages endpoint remains unchanged; callers use the request ID to read structured request data.

```json
{
  "request_id": "90000000-0000-4000-8000-000000000091",
  "session_id": "90000000-0000-4000-8000-000000000090",
  "source_message_id": "90000000-0000-4000-8000-000000000092",
  "request_status": "RECEIVED",
  "requested_at": "2026-10-31T08:00:00Z",
  "generation_started": false,
  "occasion": "đi làm",
  "intended_use": null,
  "style_context": "tối giản",
  "general_weather_context": null,
  "max_budget_vnd": 850000,
  "budget_is_hard": true,
  "constraints": [
    {
      "constraint_kind": "EXCLUDE_COLOR",
      "catalog_entity_id": null,
      "text_value": "RED",
      "is_hard": true
    }
  ]
}
```

| Condition | Proposed status/code | Write behavior |
|---|---|---|
| Missing/invalid JWT | 401 | No read/write of private resources |
| Valid JWT without CUSTOMER/ADMIN | 403 | No write |
| Missing or foreign session/request/source message | 404 `RESOURCE_NOT_FOUND` | No write; no existence leak |
| Malformed JSON, unknown/duplicate field, wrong type, unsupported constraint shape | 400 `INVALID_REQUEST` | No write |
| No meaningful request text/context; hard budget without amount | 400 `INVALID_CONTEXT` | No write |
| Unsupported media type / body too large | 415 / 413 | No write |
| Session closed | 409 `SESSION_CLOSED` | No write |
| Successful intake only | 201 | One atomic request, optional USER message, and constraint set |
| Local transaction failure | 500 safe Problem Details | Roll back all rows in the intake transaction |

No automatic retry, same-payload deduplication, exactly-once guarantee, or `Idempotency-Key` is promised. A client retry after losing the 201 response may create a second request and USER message; the client should avoid blind POST retries. A separate idempotency design would need its own owner-approved key, digest, uniqueness and retention rules.

## 4. Transaction and lifecycle proposal

- Authenticate and validate the body before opening the write transaction. Inside a short transaction, re-check `(session_id, JWT sub, closed_at IS NULL)` and validate a referenced USER message against the same session. Lock the session row for the write so a future close operation cannot race request admission. ADMIN remains self-only.
- If `message` is present, create one USER message with server UUID/time; set `source_message_id` to that row. If a prior message ID is supplied, validate and reference it without duplicate message content. Insert the request and all constraints in the same transaction. Validation or transaction failure produces no partial rows.
- No Identity, Commerce, Wardrobe, Qdrant, Neo4j, image fetch, provider, or other remote call occurs inside this transaction. P03-B1 makes no remote call.
- If a source-message reference fails, return the same 404 as a foreign/missing session. A user does not learn that another session/message exists.
- `RECEIVED` is the only request state in V2. Later P07 vocabulary such as `PROCESSING`, `SUCCEEDED`, `NO_RESULT`, `FAILED`, and `INTERRUPTED` remains unapproved and is not exposed as working behavior. An executor transition, single-in-flight policy, recovery cutoff, retries, deadlines, and final result transaction need an owner-approved lifecycle before generation code.
- For future generation, remote profile/catalog/owned-context resolution should finish before a short final Stylist transaction; never hold a local DB lock across HTTP or model calls. If a dependency fails, fail closed and do not return catalog-only/AI success as a substitute. If an accepted request cannot be processed, retain an honest stored status and expose a safe error/status contract only after that lifecycle is approved.
- Concurrent intake may create multiple `RECEIVED` requests. The first slice has no one-active-generation rule. P07 must decide whether to serialize per session, how to fence late workers, and what GET shows after timeout/crash/commit-response loss. There is no automatic reprocessing of a `RECEIVED` request in this proposal.

## 5. Approved P03-B1 Stylist schema delta

The logical target remains [Stylist model](../../../database/six-service-model/05_stylist.mmd); the physical V1 is only the two existing session/message tables. No field below exists physically today except `stylist_session` and `stylist_message` fields in V1.

### V2 — exactly two tables: `styling_request` and `styling_constraint`

| Table / column | Proposed PostgreSQL type | Null/default | Notes |
|---|---|---|---|
| `styling_request.styling_request_id` | UUID | PK, not null | Server-generated |
| `styling_request.stylist_session_id` | UUID | not null | Local FK to `stylist_session` |
| `styling_request.source_message_id` | UUID | null | Composite local FK `(stylist_session_id, source_message_id)` to V1 unique `(stylist_session_id, stylist_message_id)`; service also checks `speaker_role='USER'` |
| `styling_request.occasion` | TEXT | null | App limit 128 code points |
| `styling_request.intended_use` | TEXT | null | App limit 128 code points |
| `styling_request.style_context` | TEXT | null | App limit 128 code points |
| `styling_request.general_weather_context` | TEXT | null | Manual text only; app limit 1,000 |
| `styling_request.max_budget_vnd` | BIGINT | null | CHECK null or `>= 0`; exact whole VND |
| `styling_request.budget_is_hard` | BOOLEAN | not null, default false | CHECK `budget_is_hard = false OR max_budget_vnd IS NOT NULL` |
| `styling_request.request_status` | TEXT | not null | CHECK only `RECEIVED` in this intake slice; later lifecycle needs a new approved migration |
| `styling_request.requested_at` | TIMESTAMPTZ | not null | Server UTC instant |
| `styling_constraint.constraint_id` | UUID | PK, not null | Server-generated |
| `styling_constraint.styling_request_id` | UUID | not null | Local FK to request |
| `styling_constraint.constraint_kind` | TEXT | not null | Proposed closed exclusion vocabulary above |
| `styling_constraint.catalog_entity_id` | UUID | nullable | Logical Commerce ID; no cross-service FK |
| `styling_constraint.text_value` | TEXT | nullable | Color code only for known `EXCLUDE_COLOR`; bounded by app |
| `styling_constraint.is_hard` | BOOLEAN | not null | Hard filter versus soft preference |

Proposed constraints/indexes: PKs; request FK to session; same-session composite source-message FK; nonnegative budget and hard-budget implication checks; status check for `RECEIVED`; one-of-two constraint value check; kind/value-type check; maximum 20 constraints and duplicate canonical entries enforced in service; index on request `(stylist_session_id)` and child `(styling_request_id)`; no cross-service FK. The public request DTO admits only the four known exclusion kinds; internal P02 handling of unknown soft raw codes is not an extra public API value. Use restrict/no-action delete semantics unless retention/delete behavior is approved. Owner isolation follows request → session → `customer_id`, not a duplicated client-supplied owner column.

### Owned-context extension (future migration after Wardrobe contract)

The target model also contains nullable remote refs `wardrobe_id` and `requirement_wardrobe_id`, plus `REQUEST_WARDROBE_ITEM(styling_request_id, wardrobe_item_id)`. Defer these columns/table from the first shop/manual slice until Wardrobe can validate identity and access. A later owner-reviewed migration may add nullable UUID columns and a composite PK on `(styling_request_id, wardrobe_item_id)`; `wardrobe_item_id` is a logical Wardrobe ref only, without a cross-service FK. Never store an outfit ID or variant ID in these columns. This deferral leaves R93/R95 and the owned requirement open; it does not remove them.

P03-B1 adds a new Flyway V2 implementing only these two tables and their local constraints/indexes. V1 remains immutable. V2 is applied only by disposable Testcontainers integration tests in this slice; no persistent project database is migrated.

## 6. Identity consumer proposal and current JWT/profile contract

**Direct source review:** Identity's current `/api/v1/me/style-profile` GET requires CUSTOMER or ADMIN, reads only the authenticated JWT subject, and does not create profile data. `StyleProfileResponse` serializes the outer `style_profile` property even when null. A present profile returns nullable `fit_preference`, `height_cm`, `weight_kg`, `usual_top_size`, `usual_bottom_size`, `shoe_size`, and arrays `style_codes`, `color_codes`, `brand_ids`. Arrays are sorted by their stored code/UUID. The route's controller returns a DTO rather than accepting an owner ID. Identity does not verify that `brand_ids` still refer to active Commerce brands.

**Consumer proposal — NOT APPROVED:** do not call Identity during P03-B intake. If a later generation request is explicitly approved to use profile preferences, call the fixed trusted Identity base URL at `/api/v1/me/style-profile` after owner validation, using only that request's bearer token. Never accept a profile owner ID from request JSON, send the token to any other host/provider, or log/cache the response across users. Treat `style_profile: null` as “no saved preferences” and continue without preference evidence. For a present response, use only owner-approved mapped style/color/brand values as soft ranking evidence; do not make profile values hard constraints. Do not send height/weight or shoe measurements to an AI provider under this proposal.

A 401/403, subject 404, timeout/5xx, malformed shape, or missing required response field must fail the profile-dependent generation path closed with a safe dependency error; do not silently claim profile use or switch to a different owner. Whether that path maps all these cases to a generic 503 `CONTEXT_UNAVAILABLE` is an owner decision. The current endpoint is a user-bearer API, not an Identity service-token profile route. Its controller does not itself set `Cache-Control: no-store`; the Stylist consumer must not cache it, and actual response-header behavior needs runtime verification.

### Identity drift reviewed for Stylist

The current JWT issuer/audience/RS256 signing and verification shape matches Stylist's configured contract: `iss`, `aud`, UUID `sub`, `iat`, `exp`, UUID `jti`, and `roles` containing CUSTOMER/ADMIN; Identity publishes a JWKS route with the matching key ID. Refresh-token revocation does not revoke an already issued access JWT before its expiry, so Stylist authorization follows access-token expiry. This is a source review, not a new Identity runtime/login test.

The planning validator still reports committed-source drift at the eight Identity paths listed in §10. The relevant profile, JWT, and JWKS paths were inspected; the unrelated address/admin/bootstrap changes were not fully re-audited. No Identity file or `reviewed_head` is changed. The prior P03-A report records an observed Notification email failure mapping change to 503/504; it does not affect Stylist's profile/JWT consumer. Keep the warning visible until the full range is reviewed separately.

## 7. Commerce and Wardrobe dependency readiness

### Commerce — source facts

Current service-local read APIs include:

- `GET /api/v1/categories` and `/api/v1/categories/{id}`; DTO `id`, `name`, `slug`, `is_active`.
- `GET /api/v1/brands` and `/api/v1/brands/{id}`; same reference DTO.
- `GET /api/v1/products` (active listing with bounded page/size and filters including `category_id`, `demographic_segment`, price bounds) and `GET /api/v1/products/{productId}`. `ProductResponse` has `product_id`, `category_id`, `brand_id`, category/brand names, name, description, `demographic_segment`, `product_status`, variants.
- `POST /api/v1/variants/batch-lookup` takes up to 100 distinct IDs and requires a user JWT. Returned `VariantResponse` has variant/product IDs, SKU, size, color, material, `unit_price_vnd`, `variant_status`, and image records.
- Internal `POST /internal/v1/variants/batch-lookup` requires `X-StyleMind-Service-Token`; its current item DTO includes variant/product IDs and names, product/variant status, color, size, current price, primary image URL, and `purchasable`. It does **not** include category/brand IDs, demographic segment, material, category/brand status, or full image metadata.

Current `demographic_segment` is a syntax-checked string, not a source-enforced women's-only taxonomy. No Commerce field or API gives the approved Stylist slot TOP/BOTTOM. Category and brand status must be read from their owning DTOs; no batch is present in the inspected controllers. Existing product/variant payloads are useful catalog facts, not a reviewed category-to-slot mapping or a consistent complete snapshot. Current price/status comes from Commerce; a retrieval index or CSV copy is only a snapshot and cannot authorize a final price/status claim. There is no stock/inventory quantity contract.

**Needed before live retrieval:** Commerce owner supplies allowed demographic codes and stability semantics, a complete category reference/export, slot-review mapping, product/variant snapshot or bounded API plan, image URL/access contract, and later change-event/version contract. Unknown/unreviewed category mapping is ineligible; never infer TOP/BOTTOM from a category name using AI.

### Wardrobe — source facts and producer proposal

Current `POST /api/v1/me/outfits` accepts `name`, `creation_source` (`MANUAL`/`AI`) and 1–10 unique `items[].variant_id`; it validates current purchasability, and `/me/outfits/{outfitId}` returns saved outfit items keyed by `outfit_item_id` and `variant_id`. Existing V1 stores `outfit(owner_account_id, creation_source, ...)` and `outfit_item(variant_id, display_order, ...)`. This represents saved shop variants. `creation_source=AI` is caller-supplied and does not prove Stylist recommendation provenance or purchase ownership. There is no current `WARDROBE_ITEM`, owned/saved discriminator, selected-owned-item resolver, or saved requirement producer in the inspected Wardrobe controller/migration/source.

The Wardrobe team should publish a versioned contract before owned requests are accepted: how callers authenticate; how the producer derives the owner; how it distinguishes `CONFIRMED_OWNED` from `SAVED`/`UNKNOWN`; which selected `wardrobe_item_id` maps to which Commerce `variant_id`; permission/revocation behavior; and how it reports missing, foreign, deleted or inaccessible items without leaking another user's data. A response must be generated from Wardrobe's own records and include a producer revision/access decision. Stylist must treat only an explicit trusted `CONFIRMED_OWNED` result as zero additional cost. A URL/route, token scope, DTO and consistency guarantee remain TBD for the producer owners to approve; fixtures/stubs are not live ownership evidence.

## 8. Approved first implementation slice — P03-B1

**P03-B1 — shop/manual request intake and read-back, no generation.** This approved slice proceeds without a live owned-item producer and does not drop the owned requirement from EP-07.

- Implement only the approved create/read request endpoints and owner-scoped service/repository path.
- Add the approved Flyway V2 for `styling_request` and `styling_constraint`; keep V1 immutable. Do not add wardrobe refs/table in this slice because there is no producer contract.
- Accept manual text/context, full-replacement constraints, and exact VND budget. Do not call Identity or Commerce during intake; no fake profile/catalog authority is needed to store a request.
- Append a USER message only when the caller supplies `message`; preserve structured-only requests without synthesizing chat content. Verify source message owner/session/role. Store request and constraints atomically.
- Restrict request-resource GET to CUSTOMER/ADMIN and keep ADMIN self-only. Apply the existing 32 KiB body policy to the nested POST and set `no-store` on request success and Problem Details paths.
- Verify strict JSON, bounded body, Problem Details, A/B+ADMIN isolation, malformed/foreign/closed-session negatives, transaction rollback, and P02 regression. Postman/Newman runtime is a separate evidence layer.
- No recommendation, AI, retrieval, profile, owned anchor, Wardrobe save, event, background queue, retry, or result status is in this slice. P07 generation must wait for its own accepted contract and working executor.

**Separate gates still open:** Wardrobe producer semantics and a later schema/API decision are required for the owned path. Profile-use consent/default, provider privacy, generation lifecycle/concurrency/recovery, catalog taxonomy, image rights/storage, Qdrant/Neo4j inputs and model budget remain distinct decisions. No missing producer blocks P03-B1; these gaps block claiming live owned-context or generation completion.

## 9. Minimum member handoff

- **Commerce:** current read DTOs and differences are in §7; provide category/demographic code inventory, mapping reviewer, export and image-access/rights details. Do not send a manually invented Stylist slot as if it were a Commerce field.
- **Wardrobe:** publish an owned-context contract with explicit confirmed-owned status and per-user access/revocation semantics; keep saved outfits distinct. No current outfit row is a substitute.
- **Business reviewers:** review category-to-slot rows, synthetic outfit judgments, constraints vocabulary, budget outcomes and image-use rights. Keep pending/unreviewed rows out of retrieval and hard-rule acceptance.
- **Stylist:** maintain separate synthetic, reviewed-catalog, live-source and human-quality evidence. Do not claim model training is required; no training decision is present.

## 10. Review and status

Direct source review for this memo covered Stylist V1/session controller/service/repositories/security/parser/filter/tests, current Identity JWT/profile DTO/controller/service, Commerce catalog/product/internal lookup DTOs/controllers/storage/config, Wardrobe outfit controller/validator/DTO/migration, Gateway route prefix, and the current Stylist logical target model. Backlog R93–R102 was read-only from `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx`, sheet `Backlog`; workbook SHA-256 remained `1f9657c9e6df2992572e86338cb4c985c21451ed4fcca08d338fb00c6b7cde73`.

Planning validator baseline before these edits: both `node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P03` exited 1, solely for committed-source drift in:

- `identity-service/src/main/java/com/stylemind/identity/bootstrap/LocalComposeAdminBootstrapService.java`
- `identity-service/src/main/java/com/stylemind/identity/client/notification/NotificationEmailClient.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/AddressController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/AdminUserController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/AuthController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/InternalAccountController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/JwksController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/ProfileController.java`

P03-B1 implementation and its disposable test evidence are recorded in `docs/planning/stylist/EP-07_P03B1_IMPLEMENTATION_REPORT.md` and the execution manifest. After targeted review and tests, the manifest fingerprint for the changed Stylist `SecurityConfiguration.java` was refreshed; `reviewed_head` and all Commerce fingerprints were left unchanged. P03 remains **PARTIAL**; US-07.01 and EP-07 remain **INCOMPLETE** until their broader owned-context, generation and acceptance requirements are resolved and verified. The planning validator's Commerce source/fingerprint drift remains unresolved and unaccepted.
