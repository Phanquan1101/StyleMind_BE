# EP-07 P03 — Preparation and owner decisions

> **Superseded in part by owner Prompt 08 (2026-10-29):** The preparation-only status below is historical. P03-A session/history is now explicitly authorized and implemented; see [P03-A handover](EP-07_P03A_SESSION_HISTORY_REPORT.md). The approval covers only two APIs and two tables. It does not authorize or complete the rest of P03, US-07.01 or EP-07.

**Owner persistence decision, 2026-10-28:** Use Spring Data JPA/Hibernate for forthcoming Stylist persistence, superseding previous JDBC instructions. Retain Controller -> Service interface/impl -> Repository. Schema/API/P03 implementation are not approved by this framework decision. Do not refactor other services.


Date: 2026-10-28. **PREPARATION ONLY — P03 NOT APPROVED.** Owner explicitly accepted the corrected baseline and P02 results in the instruction requesting this preparation. P02 acceptance does not authorize P03 schema or HTTP contracts.

## Repository and inputs

Ran fetch then `git pull --no-rebase --no-autostash origin dev`: Already up to date. Remote dev is `a2a650747246d931ff9fa058a0d3027b263f3d15`; current branch remains `feature/ep-07-ai-stylist`, HEAD `e6cbaee0ab289e32b1e1f564db9f20a425a5e607`. Before/after hashes of working/index diffs and all 20 untracked files matched. No stash or merge commit was necessary. Existing staged planning and P02 source preserved.

Re-read plan, corrected baseline, target Stylist model, current Stylist POM/properties, Phase A schema/API/lifecycle proposals and Backlog R93–96 read-only. Workbook hash unchanged: `1f9657c9e6df2992572e86338cb4c985c21451ed4fcca08d338fb00c6b7cde73`. No remote code updates changed the producer capability gap identified in P02.

## P03 objective and complete scope in the plan

Conversation/session ownership and history, request admission and saved structured context, Identity context consumer, JPA persistence, security/validation, transaction/locking/rollback tests and Postman. R93 plans five conversation/request tables; R94–96 also need later pipeline/live integration. Recommendation persistence belongs to P07, retrieval to P04/P05, model to P06. Missing owned-status producer may be represented by clearly labeled consumer stubs, not production ownership claims.

## Decision 1 — persistence framework and migration authorization

**CONFIRMED owner decision, 2026-10-28:** Spring MVC -> feature Controller -> Service interface/impl -> Spring Data JPA Repository. JPA replaces the previous JDBC instruction. No further framework approval is required.

**Needs scoped approval:** retain Stylist's JPA starter, prepare JPA entity mappings/repositories for the approved tables, and add Flyway and PostgreSQL Flyway module consistent with existing project conventions; create Stylist V1 only for the approved tables. No other service conversion, cross-DB FK/read, auto-DDL or existing migration edits. Use PostgreSQL already selected by the project. Integration verification should apply approved migrations only to disposable PostgreSQL; persistent application requires separate explicit authorization.

## Decision 2 — scope of the first persistence slice

Two options, neither approved here:

**A — recommended, P03-A session/history first:** create only the two following tables and two public operations described below. Keep remaining R93/R94/R95 work open as P03-B/P07/P09. This is a deliberate partial delivery, not completion of P03 or US-07.01.

| Table | Exact proposed columns | Constraints/indexes |
|---|---|---|
| stylist_session | stylist_session_id UUID PK; customer_id UUID NOT NULL; title nullable text; opened_at timestamp NOT NULL; closed_at nullable timestamp | customer_id logical Identity reference, no remote FK; title if present 1–100 Unicode code points/no controls; closed_at >= opened_at; owner/id lookup index |
| stylist_message | stylist_message_id UUID PK; stylist_session_id UUID NOT NULL; speaker_role text NOT NULL; content text NOT NULL; sent_at timestamp NOT NULL | local FK session; USER/ASSISTANT CHECK; content 1–2000 code points/no controls; unique(session,id) for future same-session source-message FK; pagination index(session,sent_at DESC,id DESC) |

IDs generated server-side, timestamps server-side/UTC instants. No extra ownership flags, soft-delete, message edit/delete, retention job or automatic history deletion. closed_at starts null; no close operation added. Messages have no independent public write API in A: newly created history is empty until real request processing is implemented. No fake assistant replies.

**B — all five R93 tables now:** additionally create STYLING_REQUEST, STYLING_CONSTRAINT and REQUEST_WARDROBE_ITEM. Before approval, finalize the request/context identity and state decisions below. The old target fields wardrobe_id/requirement_wardrobe_id/wardrobe_item_id cannot be silently filled with outfit IDs or variant IDs. Do not execute the old model as-is and pretend the correction is resolved.

Recommendation tables WARDROBE_RECOMMENDATION/RECOMMENDATION_ITEM and the two knowledge tables remain outside either P03 choice.

## Decision 3 — shop-linked wardrobe identity and ownership

Both owned and saved items are shop variants; only trusted OWNED inputs cost zero. The source currently exposes outfit/outfit_item/variant IDs, not owned/saved status or a WARDROBE_ITEM identity. Required decisions before real context persistence/admission:

- Keep old target remote IDs pending a corresponding producer contract, or explicitly revise model/API to use actual outfit_id + outfit_item_id + Commerce variant_id with separate meanings. A potential request-selection relation would have local request FK plus these logical remote IDs; it is a proposal, not an approved replacement table.
- Decide whether wardrobe_id/requirement_wardrobe_id remain deferred or are explicitly revised/removed. There is no live saved-requirement producer; profile preferences are a different source. No column aliases or fabricated remote tables.
- Define the trusted source/contract for owned-versus-saved. Current wardrobe membership, creation_source=AI, client booleans and merely saving an outfit cannot establish OWNED. Do not automatically infer purchase ownership. No implementation change to Wardrobe/Commerce is authorized in P03.

Recommendation: choose A now; implement context consumer boundaries/tests with labeled fixtures as needed, defer real anchor admission until a producer supplies the required evidence. Keep the full shop-linked owned goal, rather than silently deleting it from acceptance.

## Decision 4 — public API scope and representation

Proposed A contract, requiring approval:

| API (service-local) | Input | Success |
|---|---|---|
| POST /api/v1/stylist/sessions | JSON object; optional title, omitted/null allowed, otherwise trimmed 1–100 code points/no controls; reject unknown fields | 201; session_id,title,opened_at,closed_at:null; Location identifying the created resource and usable via configured Gateway prefix |
| GET /api/v1/stylist/sessions/{session_id}/messages | limit default20, range1–50; optional opaque before cursor binding timestamp+UUID and session/owner; no body | 200; items and nullable next_cursor; message_id,speaker_role,content,sent_at. Stable newest-first sent_at/id; empty list is valid; no writes |

Gateway client prefix is `/stylist`, e.g. `/stylist/api/v1/stylist/sessions`. No routing change required. Success response recommendation is direct DTO; errors Problem Details. Owner must choose explicitly if ApiResponse<T> is desired instead, with exact schema before coding; no global response rewrite.

Prior history proposal includes derived request_ids. A has no request table: recommended A response omits request_ids; adding this field in P03-B requires an explicit contract revision. Alternative: approve all five tables first so request_ids can be a genuine queried relationship. Do not invent associations or create an empty placeholder field claiming it is implemented.

Defer POST sessions/{id}/requests and GET requests/{id} implementation until request/schema/admission is approved; defer GET recommendations/{id} to P07. No 201 successful generation, queued 202 or PROCESSING promise without a working pipeline/executor. No new POST messages, friend-chat, edit or close API inferred from the history story.

## Decision 5 — authorization, validation and context use

Proposed: CUSTOMER or ADMIN, both self-only by validated JWT sub; ADMIN has no cross-customer read. Missing token 401; valid but unsupported role 403; missing or foreign session both 404 RESOURCE_NOT_FOUND. Follow Identity issuer/audience/RS256/time/UUID validation; current scaffold is not proof of all these checks. No new global live-account gate.

Reject unknown/duplicate JSON/query keys, wrong types and invalid cursor with 400; JSON media type 415; bounded body 32 KiB with 413. User-specific responses/errors Cache-Control:no-store. Do not log message/profile content, tokens or secrets. No real-user retention/deletion guarantee is introduced.

Identity Feign client for later request context uses only the trusted fixed /me/style-profile endpoint with explicit delegated bearer, preserving null profile and scalar/array semantics; preferences remain soft. Create/history endpoints do not need profile/network calls. A consumer adapter can be tested against HTTP stubs without claiming live integration; decide whether to include it in A or defer to B. No profile aggregation, external AI upload or measure-based inference.

## Decision 6 — admission/lifecycle if B is selected

Previous proposal remains NOT APPROVED: PROCESSING/SUCCEEDED/NO_RESULT/FAILED/INTERRUPTED; one in-flight request per session, session->request lock order, short atomic local transactions, remote context/provider work outside locks. Synchronous bounded generation, proposed 30s deadline; no queue/worker/replay/idempotency guarantee. Full lifecycle belongs to an operational P07 pipeline, not a stubbed success in P03.

Before B, choose exact lifecycle vocabulary, request/message atomic admission, invalid-context no-write behavior, no-result versus failure, stale/crash recovery and handling of uncertain commit. Resolve corrected request fields and owned/saved validation first. Under A these decisions can remain deferred without blocking session/history.

## Acceptance plan and current stop

After authorization: controller/security/HTTP tests, owner A/B and ADMIN self isolation, disposable PostgreSQL migration/restart/rollback/pagination, meaningful concurrency cases for the selected scope, P02 + existing Stylist regression, Postman JSON/guide and separate structural/runtime evidence. No skipped test counts as pass. New default DB startup behavior is verified with isolated credentials/resources, not persistent data.

This preparation created no production/test code or SQL, installed no dependency and ran no application tests/Compose/migration/provider. Planning guard P03 correctly reports api/schema/phase-authorization pending. P02 owner acceptance is recorded, not re-opened.
