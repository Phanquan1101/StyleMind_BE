# StyleMind Proposed Baseline Governance

PROPOSED — HUMAN APPROVAL REQUIRED

This governance model is offered for human approval; it is not a new approved decision or a replacement for the existing confirmed constraints. The Phase 0.5 instruction authorizes this preparation and one heading clarification, not approval of options, new DEC records, product behavior or final architecture.

## Authority and evidence are different

A simple ranking by document folder or date is insufficient. Original input files can themselves contain explicit confirmed human decisions, and discovery is a synthesis that can misquote an original. Approval authority must follow the specific assertion and its approval provenance, not merely whether it appears in a newer file.

The proposed authority model has two dimensions: what approved statement controls the current specification, and what original evidence supports its history. Preserve both. A human-approved scope decision can change future behavior explicitly without deleting the older source. A discovery paraphrase cannot silently change what the original source said.

## Proposed specification authority model

| Layer | Proposed authority | Conditions and conflict handling |
| --- | --- | --- |
| 1 | Active, explicitly human-approved Decision Baseline | Controls the assertions it actually decides, with named authority, scope and explicit supersession where needed. Existing explicit confirmed human decisions retain their standing even if their provenance is in docs/input or the Phase 0 register. A later date alone is insufficient. |
| 2 | Approved business/product requirements and Business Rules | BRD/PRD/rules control business behavior, scope and invariants within their approved remit. Peer-document conflicts must be surfaced; neither title nor freshness silently resolves them. |
| 3 | Approved domain, ADR and architecture specifications | Control approved responsibilities and technical constraints within business scope. Architecture cannot redefine a business rule without an authorized, explicit higher-level decision/change. |
| 4 | Approved API/Event/Security/AI specifications and contracts | Supply approved detail within the requirements and architecture. Security/AI product assertions are assessed within their actual business or architectural remit, not automatically subordinated because of their filename. Cross-layer incompatibility requires coordinated amendment. |
| 5 | Phase 0 discovery evidence baseline | Supplies classified, traceable evidence and open questions. Its faithful transcriptions of confirmed decisions retain their provenance; unapproved derived/proposed/TBD statements do not become authoritative implementation requirements. |
| 6 | Original source materials as preserved evidence | Retain exact historical statements and context. For extraction fidelity, the original source is controlling over a discovery paraphrase. Normative authority of an explicit approved decision in an original file is determined by its approval provenance, not this evidentiary layer number. |
| 7 | Existing implementation and observed behavior | Evidence of what software does, not authority to invent or override required business behavior. Any discrepancy is reported for explicit decision. |

This refines the example ordering rather than accepting it mechanically. Approved artifacts must be mutually consistent; a layer is not permission to ignore a conflicting lower-layer safety, security or business constraint. Resolve the conflict explicitly within the appropriate human approval remit. No approval authority or new compliance regime is inferred here.

## Proposed approval responsibility

DR-001 must identify the accountable business approver and any joint/specialist approval rules. Technical, security, AI and domain reviewers should be named with explicit scopes before approving their artifacts. Actual names and delegated limits remain TBD. The instructor's feedback is evidence of product direction, not proof that the instructor is the final approver.

An agent may extract evidence, prepare options, assess consequences and propose wording. It may not sign for a human, treat silence as consent, convert a recommendation into APPROVED, or invent a DEC identifier for an unresolved choice. The current independent review PASS did not answer the 62 OPEN questions. The later human baseline explicitly resolves the ten OPEN IDs listed in the approved records and leaves all other OPEN records TBD/DEFERRED.

## Proposed change and supersession workflow

1. Identify the exact changed assertion, source cluster and affected OPEN/FR/BR/AI/ARCH/DEC identifiers; preserve the prior source and content.
2. Prepare the human decision record from file 04, including rationale, consequences and remaining uncertainties. Keep it unapproved until an authorized human chooses and records the content.
3. For an approved change, state precisely whether it clarifies, adds, rejects or supersedes an existing assertion. Explicit supersession must name the old record/requirement and the replacement's scope.
4. Apply only the approved downstream edits through a controlled change, with old and new versions and approval provenance retained. Keep stable identifiers; do not randomly renumber.
5. Cross-check impacted business rules, architecture, contracts and traceability. Surface incompatibilities before agents use the amended baseline. Code never resolves a specification conflict by default.
6. Mark an OPEN resolved only when the entire question is explicitly answered. Partial answers and deferrals stay traceable at clause level; a package-level approval is not blanket closure.

For unresolved conflicts, log both assertions, their source/approval status and the exact human choice needed. Continue unaffected preparation; block only work that depends on the disputed assertion. A superseded decision remains readable and links to its replacement. Rejected options remain evidence of considered choices, not deleted history.

## Proposed deferral discipline

An explicit human deferral should name the detail, owner, reason, latest required artifact/trigger and what dependent work remains blocked. Preserve the confirmed overall scope and the OPEN identifier. For example, postponing 3D technology cannot remove live 3D from broader scope, and postponing an AI provider cannot relax real-product grounding or budget respect.

Record no provider, topology, numerical target, weather/carrier API, data schema or service boundary merely to make a document appear complete. Agent-generated recommendations remain PROPOSED even when repeated in multiple files.

## Approved documentation clarification only

The current Phase 0.5 user instruction explicitly approved this exact replacement in docs/discovery/05_SCOPE_REGISTER.md:

- Old heading: A. CORE / INITIAL
- New heading: A. EXISTING CORE CAPABILITIES — INITIAL MILESTONE TBD

Classification: documentation clarification authorized by the user. It is not a product decision, does not allocate a DEC identifier, and changes no scope content or classification. The updated heading states the qualification already present in the section body. No other Phase 0 content is authorized for modification in this phase.

The validation process compares the old/new text with only this replacement allowed, checks the other eleven discovery files against their pre-phase hashes, and verifies that the four original input hashes are unchanged. Historical Phase 0 validation text remains preserved as part of that baseline; this Phase 0.5 change is recorded here rather than rewriting the prior validation report.

## Validation record

PASS — preparation audit. All five review documents were read back and cross-checked. The map contains 18 clusters: 4 NOW and 14 deferred to later review gates. All 62 OPEN IDs have exactly one primary owner, with no missing or duplicate assignments. Each NOW cluster has an unapproved option package; queue membership covers all clusters once (A: 2, B: 13, C: 3). The prerequisite graph has no cycles; downstream refinement references are distinguished from prerequisites.

The other eleven Phase 0 discovery files and all four original inputs matched their pre-phase hashes. The scope-register text differed only by the exact authorized heading replacement. Existing identifiers remained unchanged. No OPEN answer, approved recommendation, new DEC record, product feature, service boundary, BRD, PRD, SAD or implementation was created during Phase 0.5. The later Human Decision Baseline V1 is stored separately under docs/decisions and does not rewrite the historical Phase 0 discovery content.
