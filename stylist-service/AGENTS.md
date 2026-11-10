# Stylist execution instructions

Read the repository [AGENTS](../AGENTS.md) first. This file applies to `stylist-service/**`; it does not approve new business decisions.

## Read before editing

1. [Planning index](../docs/planning/stylist/README.md) and the requested phase in the [implementation plan](../docs/planning/stylist/EP-07_IMPLEMENTATION_PLAN.md).
2. [Contract memo](../docs/planning/stylist/EP-07_PHASE_A_CONTRACT_MEMO.md), [flows](../docs/planning/stylist/EP-07_FLOWS.md), [technical design](../docs/planning/stylist/EP-07_TECHNICAL_DESIGN.md), [dependency contracts](../docs/planning/stylist/EP-07_DEPENDENCY_CONTRACTS.md).
3. [Execution manifest](../docs/planning/stylist/EP-07_EXECUTION_MANIFEST.json), [verification matrix](../docs/planning/stylist/EP-07_VERIFICATION_MATRIX.md), owning model and actual dependency DTO/security source.

## Start each slice

- Inspect branch/HEAD/status and preserve existing changes. Fetch/merge only when instructed; no auto-stash, reset, clean, force, commit, push or secret rotation as routine setup.
- From repo root run `node scripts/verify-stylist-planning.cjs`, then `--phase Pxx` for the requested phase. Source drift requires targeted review, not automatic hash replacement. A missing workbook permits planning from the marked historical transcription; it does not permit claiming fresh workbook verification or Epic completion.
- State exact US/task rows, approved rules and allowed files. A new owner task may explicitly approve a decision; record its wording/source honestly before updating manifest evidence. Never mark a proposal APPROVED because an Agent wrote it or code implements it.
- Gate only the dependent slice. Finish independent work and report the precise missing decision instead of blocking all planning.

## Structure and behavior

- Spring MVC â†’ feature-grouped Controller â†’ public Service interface â†’ `impl` â†’ Repository. Separate orchestration from pure recommendation rules. Remote work runs outside local DB transactions; use separate Spring transaction beans to avoid self-invocation.
- Owner decision 2026-10-28: use Spring Data JPA/Hibernate, retaining the existing JPA starter. Persistence entities and repositories are feature-grouped; DTO/runtime engine models are separate. Use explicit transaction boundaries and bounded fetches to avoid N+1. Flyway owns approved DDL; no automatic schema creation/update. Do not refactor other services.
- Only `stylemind_stylist`; no cross-service DB access/FK, automatic schema generation or edits to versioned migrations.
- Derive ownership from validated JWT `sub`; ADMIN is not a cross-customer bypass. Check session/message/request/result/source ownership at service/repository boundaries. A JWK URL alone is not issuer/audience/role validation.
- Preserve approved HTTP contracts. The Stylist proposal uses direct DTO success bodies and Problem Details errors. No global `ApiResponse` wrapper or new permission framework without a contract decision. `204` has no body; JWKS/OpenAPI keep standard formats.
- Commerce owns price/status/metadata; each context owner decides access. Qdrant/Neo4j/LLM are not price/permission/physical-stock authorities. Recheck hard rules after final authoritative hydration.
- Owner correction 2026-10-29 (Prompt 08) supersedes the earlier slot scope: the current product set is women’s TOP and BOTTOM, and an outfit must contain exactly one of each. Reject all other slots, including ONE_PIECE, SHOES and ACCESSORY. Owned and saved remain distinct; only trusted confirmed owned anchors cost zero additional VND. Current Outfit API lacks owned/saved proof: fixture flags are not live evidence. Keep variant identity and wardrobe reference separate.
- Prompt 08 explicitly authorizes P03-A only: session creation and owner-scoped message-history reads; Flyway V1 containing only `stylist_session` and `stylist_message`; the two specified APIs; tests, Postman collection and run guide. This does not authorize the remainder of P03, US-07.01 or EP-07, persistent database migration, or work in another service.
- One provider adapter after model/data/budget approval. Tests default to stubs, not paid calls. No automatic model fallback, billable retry, autonomous tools, arbitrary URL fetching, full-history upload, training, Python AI service or image/body analysis.
- Shared/public read does not imply external-AI consent or write access. Denied context does not silently become catalog-only. Do not return another customer's private source names/reasons/history.

## Verification and handover

Use phase-specific tests: pure rules, actual client HTTP serialization/faults, disposable PostgreSQL atomicity/concurrency, isolated retrieval stores, then real-service/Gateway evidence. Report fixture, stub, live and E2E separately. Skipped/blocked is not PASS. Do not rerun every service for a documentation edit.

Each HTTP slice includes importable Postman JSON, placeholders-only environment, guide and separate structural/runtime results. Report exact commands/counts/environment, retained redacted failures, resources touched, changed files and remaining gates. Never log credentials, raw prompts/private context, JWT/OTP/private keys or full provider payloads.

Before handover run `git diff --check`, planning validator and requested regression gates. Update only the slice's status/evidence. Do not call all R93â€“102 DONE while owned-source, Neo4j, events or live-provider acceptance remains unsupported. No automatic merge to dev.

## Owner supplement — Accelerated Delivery 2 (2026-11-05)

The owner-approved attachment “STYLEMIND — ACCELERATED DELIVERY 2” expands the earlier P03-B1-only authorization for the exact Stylist Wave 2 scope in `docs/planning/stylist/EP-07_WAVE_2_CONTRACT.md`. It authorizes generation/recommendation APIs, V3 recommendation persistence, approved-model embedding and structured generation adapters, runtime Qdrant/Neo4j reviewed-data adapters, tests and Postman handover. It does not approve owned/profile context, Identity/Wardrobe integration, catalog events, another service, persistent DB migration, production deployment, or automatic provider calls. Provider smoke is gated by the prompt's key, data, token and $2 reservation requirements; no key means no call.

## Owner supplement — final AI Stylist handover profile (2026-11-07)

For the current approved Stylist handover, use Gemini Developer API generation model gemini-3.5-flash-lite and embedding model gemini-embedding-001 at 1536 dimensions. This supersedes the earlier gemini-2.5-flash setting for the current handover run only; historical adapter and earlier test evidence remain historical. Select Gemini explicitly with STYLEMIND_AI_PROVIDER=gemini; presence of a key must not select it. STYLEMIND_GEMINI_LIVE_CALLS_ENABLED remains false by default.

The approved meter profile reached 12 embeddings / 23 countTokens / 6 generations on 2026-11-07. Do not run more live calls under that profile. A future live run needs a new owner-approved budget/profile, verified current Free Tier conditions and a reconciled meter. Live calls must pass through the bounded local meter proxy. Never put keys in Postman, source, command history or reports.

The current code verification is 191 tests, 0 failures, 0 errors, 1 live-only skip; that skip is not a pass. The offline Gateway/Newman evidence and one positive live Gateway result are separately documented in `docs/qa/stylist/AI_STYLIST_HANDOVER_REPORT.md`. Commerce/JWKS were fixtures and JWTs were fixture-signed; no Identity-issued token, live Commerce catalog, persistent migration, or production fashion-quality acceptance is claimed. R95/R99 and EP-07 remain incomplete.

## Owner supplement — final AI Stylist handover profile (2026-11-07)

For the current approved Stylist handover, use Gemini Developer API generation model gemini-3.5-flash-lite and embedding model gemini-embedding-001 at 1536 dimensions. This supersedes the earlier gemini-2.5-flash setting for the current handover run only; historical adapter and earlier test evidence remain historical. Select Gemini explicitly with STYLEMIND_AI_PROVIDER=gemini; presence of a key must not select it. STYLEMIND_GEMINI_LIVE_CALLS_ENABLED remains false by default.

The approved meter profile reached 12 embeddings / 23 countTokens / 6 generations on 2026-11-07. Do not run more live calls under that profile. A future live run needs a new owner-approved budget/profile, verified current Free Tier conditions and a reconciled meter. Live calls must pass through the bounded local meter proxy. Never put keys in Postman, source, command history or reports.

The current code verification is 191 tests, 0 failures, 0 errors, 1 live-only skip; that skip is not a pass. The offline Gateway/Newman evidence and one positive live Gateway result are separately documented in `docs/qa/stylist/AI_STYLIST_HANDOVER_REPORT.md`. Commerce/JWKS were fixtures and JWTs were fixture-signed; no Identity-issued token, live Commerce catalog, persistent migration, or production fashion-quality acceptance is claimed. R95/R99 and EP-07 remain incomplete.
