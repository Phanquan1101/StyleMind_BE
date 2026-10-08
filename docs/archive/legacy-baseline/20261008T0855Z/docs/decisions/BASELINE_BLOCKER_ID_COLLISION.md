# Decision Baseline Blocker — Decision ID Collision

Status: RESOLVED

Resolution recorded: 2026-09-21. The collision was detected before the four new decisions were persisted. Existing DEC-001 through DEC-025 records were preserved exactly. New authoritative IDs were allocated sequentially as DEC-026 through DEC-029. No historical decision was modified, deleted, reinterpreted or superseded.

## Conflict

The requested approved decisions reuse four identifiers that already have different confirmed meanings in the Phase 0 decision register:

| ID | Existing Phase 0 meaning | Requested approved meaning |
| --- | --- | --- |
| DEC-001 | The project is named StyleMind. | Business ownership and success baseline; resolves OPEN-001. |
| DEC-002 | Position the product as an AI Personal Stylist and Smart Wardrobe commerce platform. | Target segment and first milestone boundary; resolves OPEN-002 and OPEN-003. |
| DEC-010 | Include travel-aware and trip-duration-aware outfit planning in the product direction. | Smart Wardrobe capability boundary; resolves OPEN-032 through OPEN-034. |
| DEC-012 | Provide strong visual outfit presentation. | Outfit promises and AI acceptance approach; resolves OPEN-041 and OPEN-047 through OPEN-049. |

Evidence: `docs/discovery/09_DECISION_REGISTER.md`, rows DEC-001, DEC-002, DEC-010 and DEC-012; the current approved-decision instruction supplied for this run.

## Why this blocks the requested baseline

The existing governance requires stable identifiers, explicit supersession and preservation of historical records. These are different decision subjects, so the requested records cannot safely be written under the already-used IDs. Overwriting the old rows would erase their historical meaning; appending a second row with the same ID would make traceability ambiguous. This is a specification-governance conflict, not a TBD detail.

The conflict also prevents safe synchronization of `OPEN-001`, `OPEN-002`, `OPEN-003`, `OPEN-032`, `OPEN-033`, `OPEN-034`, `OPEN-041`, `OPEN-047`, `OPEN-048` and `OPEN-049`, because their approval records would not have unique decision identities.

## Resolution

The human owner approved allocation of four unused identifiers in source-DR order:

1. DR-001 → DEC-026
2. DR-002 → DEC-027
3. DR-010 → DEC-028
4. DR-012 → DEC-029

No semantic supersession relationship exists. The old records retain their original meanings.

## Downstream status

- Four approved decision records: created as DEC-026 through DEC-029.
- Decision Baseline V1: created and marked COMPLETE.
- Decision Register, Open Questions and Traceability Seed: reconciled to the new IDs.
- BRD: created after the baseline consistency check passed.
- Architecture, service boundaries, API contracts, database schema and implementation: not started.
