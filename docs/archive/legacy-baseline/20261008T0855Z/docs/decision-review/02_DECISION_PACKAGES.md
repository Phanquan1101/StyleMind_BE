# StyleMind Human Decision Packages

The four packages below are preserved historical review material for the NOW clusters in file 01. The human owner has now approved the resulting decisions, recorded as DEC-026 through DEC-029. The options and recommendations in this file remain historical proposals and are not themselves authoritative. Relative complexity/risk is a DERIVED comparison, not an estimate or delivery commitment.

The options adjust approval workflow, prioritization or the depth of already identified StyleMind capabilities. They do not add product features. Any later detailed behavior still requires evidence or explicit approval. Package dependencies must be reviewed in order: DR-001, DR-002, DR-010, DR-012.

## DR-001 Business ownership and success baseline

Decision statement / question: Who has authority to approve the business baseline, and how will that human define and accept success for the confirmed commerce and stylist objectives?

Why needed: a scope or acceptance choice is not actionable without an accountable approver and an agreed purpose. This decision can settle approval ownership and the success framing together rather than routing separate versions of OPEN-001 to multiple meetings.

Evidence from Phase 0: file 01 FCT-002, FCT-004 and FCT-050; file 02 BUS-001 through BUS-004; file 06 OPEN-001; file 09 DEC-022 and DEC-023. The discovery identifies customer/admin actors and instructor feedback, but not a named business approver or measurable success targets.

Related OPEN IDs: OPEN-001.

Related FR/BR/AI/ARCH IDs: FR-036, AI-003, ARCH-008; BR-032 supplies the already confirmed product-grounding constraint. BUS-001 through BUS-004 frame the outcomes, not preselected metrics.

Constraints already confirmed: women-first commerce/styling direction, retained commerce lifecycle, source-grounded recommendations and explicit, traceable approval/change handling.

What MUST NOT be assumed: the instructor is the final product approver; the current user has a particular organizational role; a revenue, conversion or accuracy number is approved; a technically successful demo is sufficient business acceptance.

| Option | Description | Advantages | Disadvantages | Downstream impact | Complexity/risk |
| --- | --- | --- | --- | --- | --- |
| A | Name one accountable business approver, with technical/domain reviewers consulted within their expertise; that approver records outcome definitions and accepted evidence for the existing objectives. | Clear decision accountability; one place to resolve product tradeoffs. | Requires an available person able to own decisions and obtain specialist input. | BRD gets a named approval path; later architecture/security approval remains separately scoped. | Lower coordination effort; risk of bottleneck if the named person is unavailable. |
| B | Require explicit joint business and technical sign-off for the baseline, with an agreed escalation rule and outcome evidence recorded jointly. | Makes cross-domain disagreement visible before acceptance. | More coordination and a potential deadlock without an escalation rule. | BRD and subsequent baseline changes need both named authorities within their assigned scope. | Higher coordination effort; reduced unilateral-change risk if duties are clear. |

Recommended option: A. RECOMMENDATION — NOT APPROVED. A single accountable business approver with explicit specialist review fits the need for traceable agent instructions without inventing a new organizational structure. The actual person, reviewer authority and success definitions remain human inputs.

Human decision required: [ ]

Approved option: TBD

Decision notes: TBD

Inputs still required: named approver/reviewers, scope of their authority, business outcome definitions, accepted evidence and any human-approved measurable targets. Reviewing this package alone does not answer OPEN-001. Detailed AI test rubrics belong to DR-012; operational revenue formulas belong to DR-007/DR-018.

## DR-002 Target segment and first milestone boundary

Decision statement / question: Which human-defined target segment and capability boundary should the first milestone serve within the confirmed women-first direction?

Why needed: the initial milestone must be distinguished from the full retained commerce and expanded styling scope. A single scope decision can constrain many later policy and technical discussions without selecting their implementation.

Evidence from Phase 0: file 01 FCT-002, FCT-003, FCT-010, FCT-012, FCT-038 and FCT-045; file 05 scope register; file 06 OPEN-002 and OPEN-003; file 09 DEC-003, DEC-004, DEC-005 and DEC-013. Vietnam address behavior and demographic filtering do not independently establish a target age range or exclusive sales territory.

Related OPEN IDs: OPEN-002, OPEN-003. Downstream refinement references, not prerequisites or primary ownership: DR-010 supplies owned-clothes scope after this milestone direction; DR-014 later refines travel/visual detail.

Related FR/BR/AI/ARCH IDs: FR-009, FR-016, FR-017, FR-018, FR-037, FR-038, AI-003, ARCH-008, UX-002; BR-032, BR-033.

Constraints already confirmed: women-first; e-commerce lifecycle retained overall; Smart Wardrobe and travel remain overall direction; live 3D remains broader scope without an automatic first-milestone commitment; body measurements are optional.

What MUST NOT be assumed: an age band, country exclusivity, style taxonomy, delivery deadline, a complete first-release commerce checklist, or that a deferred capability is out of project scope. No option below implies approval of weather, packing optimization, photorealistic try-on or another unapproved feature.

The milestone options are alternatives for emphasis, not approved feature lists. For every option, humans must supply the target segment/geographic coverage and decide how the existing demographic filter behaves. The source does not support prefilled segment values, so those remain TBD across all options.

| Option | Description | Advantages | Disadvantages | Downstream impact | Complexity/risk |
| --- | --- | --- | --- | --- | --- |
| A | Prioritize a human-selected commerce slice and the existing catalog-grounded AI stylist in the first milestone; schedule the owned-clothes and travel experience later. | Uses the most detailed existing workflow evidence; reduces concurrent new behavior to define. | Demonstrates less of the Smart Wardrobe positioning initially. | DR-010 must record a compatible Wardrobe deferral; full commerce lifecycle, Wardrobe, travel and 3D remain overall scope. | Lower relative specification uncertainty, but commerce correctness and AI grounding still have substantial blockers. |
| B | Prioritize a human-selected commerce slice plus a bounded owned-clothes/catalog styling experience; decide the Wardrobe boundary in DR-010. | Shows the confirmed combined commerce/Wardrobe identity early. | Requires item semantics, data-use policy and composition behavior before PRD/domain work can be finalized. | Makes DR-010, DR-011, DR-012 and DR-015 early dependencies; travel and 3D timing must be recorded explicitly. | Medium relative scope/definition risk; not a claim of low implementation effort. |
| C | Include the same commerce/Wardrobe emphasis and a defined travel-styling slice in the first milestone. | Exercises the travel and trip-duration direction alongside owned-clothes styling. | More context and constraint decisions are required before coherent acceptance criteria can be written. | DR-014 must resolve the included travel behavior before PRD; no weather or packing capability follows automatically. | Higher relative definition and acceptance risk because travel details are currently sparse. |

Recommended option: B. RECOMMENDATION — NOT APPROVED. It represents the women-first Smart Wardrobe product identity while keeping the first milestone boundary subject to explicit definition. Actual feasibility, available effort and acceptance thresholds are not known, so a human may choose A or C instead.

Human decision required: [ ]

Approved option: TBD

Decision notes: TBD

Inputs still required: target segment and geography, demographic-filter treatment, exact included commerce/styling capabilities, explicit deferrals and the initial acceptance boundary. A milestone emphasis alone does not close all of OPEN-002 or OPEN-003. DR-017 can later reveal feasibility constraints; any resulting scope change needs explicit reconsideration.

## DR-010 Smart Wardrobe capability boundary

Decision statement / question: Should the first milestone include a bounded owned-clothes plus catalog styling experience, or should the Wardrobe experience remain scheduled later while its detailed behavior is specified?

Why needed: Smart Wardrobe is confirmed as product direction, but its item operations, fields and optional image/history/favorite behavior remain undefined. A scope decision can identify which parts need detailed review before PRD without treating every unknown as a new feature request.

Evidence from Phase 0: file 01 FCT-038; file 02 FR-037, FR-039, DATA-006 and AI-018; file 06 OPEN-032 through OPEN-034; file 09 DEC-005. The distinction between owned items and purchasable recommendations is derived in AI-018; no item schema or capture mechanism is confirmed.

Related OPEN IDs: OPEN-032, OPEN-033, OPEN-034.

Related FR/BR/AI/ARCH IDs: FR-037, FR-039, DATA-006, AI-003, AI-018, ARCH-003; BR-032, BR-033.

Constraints already confirmed: styling may consider both already owned clothes and purchasable StyleMind products; real catalog grounding; optional body measurements; final service boundaries unresolved.

What MUST NOT be assumed: mandatory manual entry, automatic import from orders, photo upload, image recognition, clothing segmentation, ownership of a new wardrobe-service, specific metadata fields, or approval of favorites/saved outfits/history. These are either existing TBD topics or unsupported implementations, not approved features.

| Option | Description | Advantages | Disadvantages | Downstream impact | Complexity/risk |
| --- | --- | --- | --- | --- | --- |
| A | Include only the confirmed owned-clothes/catalog styling purpose as a bounded first-milestone capability, then define its necessary item semantics and approved operations before PRD. | Gives a concrete focus for the combined product identity without pre-approving optional features. | Still requires human answers on input method, representation, data use and acceptance before the capability is implementable. | Compatible with DR-002 option B or C; activates DR-011, DR-012 and DR-015 review. All unchosen image/history/favorite details remain TBD. | Medium relative uncertainty; a boundary choice is not a completed Wardrobe specification. |
| B | Retain Smart Wardrobe in overall scope but defer its first operational slice pending item/capability definition; initially use the catalog-styling emphasis selected in DR-002. | Allows the mature source-described commerce/catalog behavior to be specified first. | Delays demonstration of owned-clothes value and leaves Wardrobe detail open longer. | Compatible with DR-002 option A; if B/C was selected there, explicitly revise that choice before approval. Schedule a later Wardrobe decision trigger rather than deleting scope. | Lower immediate definition load; higher risk of an indefinite deferral unless a human owner/trigger is recorded. |

Recommended option: A, conditional on a compatible DR-002 choice. RECOMMENDATION — NOT APPROVED. It aligns with Smart Wardrobe's explicit importance while preserving all unresolved item behavior. If DR-002 chooses catalog-only emphasis, option B is the compatible alternative and the recommendation must be reconsidered by the human, not silently switched by the agent.

Human decision required: [ ]

Approved option: TBD

Decision notes: TBD

Inputs still required: capability inclusion, approved item input/operations, owned-item/catalog relationship, necessary metadata and an explicit disposition for each optional topic. Approval of timing alone does not resolve OPEN-032 through OPEN-034. The later data owner and service boundary remain DR-016 decisions.

## DR-012 Outfit promises and AI acceptance approach

Decision statement / question: How should humans establish the outfit acceptance baseline and sequence its behavior, evaluation and runtime decisions for the chosen milestone?

Why needed: budget respect, real-product grounding and explanation are confirmed, but complete outfits, competing constraints, evaluation evidence and runtime limits are not defined. One acceptance approach can organize these related questions without forcing a provider decision now.

Evidence from Phase 0: file 02 AI-004, AI-005, AI-015 through AI-017; file 03 BR-032, BR-033, BR-034, BR-042; file 06 OPEN-041 and OPEN-047 through OPEN-049; file 10 GAP-019 and GAP-021. The existing source does not provide a dataset, numerical thresholds or model/provider choice.

Related OPEN IDs: OPEN-041, OPEN-047, OPEN-048, OPEN-049. Downstream refinement reference, not a prerequisite: DR-013 owns pipeline, grounding mechanics and synchronization questions; they are not reassigned here.

Related FR/BR/AI/ARCH IDs: FR-037, AI-003, AI-004, AI-005, AI-015, AI-016, AI-017, ARCH-008; BR-032, BR-033, BR-034, BR-042.

Constraints already confirmed: purchasable products must be real StyleMind catalog products; a supplied budget must be respected; explanations are required; body measurements remain optional; knowledge requires approval before production if used.

What MUST NOT be assumed: a model/provider, exact latency/cost/accuracy target, number of outfit items, a hard/soft priority order, a specific fallback response, or a complete Wardrobe/travel algorithm. Selecting an evaluation approach is not approval to start writing the final AI Specification now.

| Option | Description | Advantages | Disadvantages | Downstream impact | Complexity/risk |
| --- | --- | --- | --- | --- | --- |
| A | Agree acceptance dimensions around confirmed promises first, then obtain human definitions/examples for outfit completeness, budget meaning, constraint conflicts and acceptable evidence; defer provider selection until requirements are known. | Separates product acceptance from technology preference; makes evidence gaps explicit. | Requires deliberate later work to turn broad dimensions into measurable criteria and examples. | DR-007/DR-011 provide money/personalization semantics; DR-013 and DR-017 receive approved acceptance inputs. No OPEN closes merely from choosing this process. | Lower risk of provider-led requirements; moderate later specification effort. |
| B | Define a small human-selected set of representative milestone scenarios first, then derive a proposed acceptance rubric and runtime feasibility questions for human review. | Makes discussion concrete for the chosen commerce/Wardrobe slice. | Scenarios can omit edge cases and must be expanded before claiming general acceptance. | Human reviewers must check coverage of confirmed budget, grounding, optional data and explanation rules; then resolve evaluation thresholds and runtime constraints separately. | Lower initial discussion overhead; higher coverage risk if examples are mistaken for a complete test strategy. |

Recommended option: A. RECOMMENDATION — NOT APPROVED. The project's specification-led agent workflow benefits from agreeing what must be true before selecting a runtime. Representative scenarios from B may later support the evidence, but that does not automatically approve a hybrid option or a test dataset.

Human decision required: [ ]

Approved option: TBD

Decision notes: TBD

Inputs still required: acceptance owner, the selected approach, actual outfit/constraint definitions and a schedule for rubric/evidence approval. Budget interpretation and precedence remain open until specifically answered. Specific provider/model, exact runtime budgets and implementation mechanisms deliberately remain later subquestions of OPEN-047.

## Completion discipline

The historical package fields remain unfilled by design. The authoritative human choices are in [DEC-026](../decisions/DEC-026.md) through [DEC-034](../decisions/DEC-034.md) and [Decision Baseline V1](../decisions/00_DECISION_BASELINE_V1.md). The [blocker queues](03_BLOCKER_REVIEW.md) distinguish the nine resolved product decision clusters from the nine later deferred clusters; technical details are not silently promoted.
