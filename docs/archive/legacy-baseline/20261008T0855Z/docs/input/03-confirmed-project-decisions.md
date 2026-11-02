# StyleMind - Confirmed Project Decisions

## 1. Product Identity

Project name:

StyleMind

Current product direction:

StyleMind is a women-first AI Personal Stylist & Smart Wardrobe commerce platform.

The product combines:

- fashion e-commerce,
- personal wardrobe,
- AI outfit recommendation,
- brand-aware styling,
- size and fit personalization,
- color coordination,
- personal style preferences,
- travel outfit planning,
- visual outfit presentation,
- advanced 3D visualization.

The existing e-commerce lifecycle remains part of the project.

This includes areas such as:

- authentication,
- user profile,
- address,
- catalog,
- product,
- product variants,
- cart,
- checkout,
- payment,
- order,
- cancellation,
- return,
- refund,
- notifications,
- administration,
- auditability.

---

## 2. Target Direction

The primary fashion focus is:

Women-first.

This does NOT automatically mean the platform can never support other groups in the future.

The purpose of the current direction is to allow deeper specialization in:

- women's styling,
- wardrobe coordination,
- size and fit,
- color matching,
- body-aware styling,
- travel outfit planning.

---

## 3. Smart Wardrobe

Smart Wardrobe is part of the intended StyleMind product direction.

The objective is to allow the stylist experience to consider:

1. clothes already owned by the user,

and

2. purchasable products from the StyleMind catalog.

The exact Smart Wardrobe feature set is NOT yet fully specified.

Missing behavior must remain TBD until formally approved.

---

## 4. Brand

Products should support brand information.

AI styling may consider brand preference where appropriate.

Detailed brand management rules and brand-specific sizing behavior are not yet fully specified.

---

## 5. Size and Fit

Size and fit are important personalization dimensions.

Potential body/fit information includes:

- height,
- bust,
- waist,
- hip,
- usual top size,
- usual bottom size,
- shoe size,
- preferred fit.

Bust, waist and hip measurements are OPTIONAL personalization information.

They are NOT mandatory registration fields.

The exact recommendation algorithm is not yet decided.

Brand-specific size charts may be considered but must not be treated as fully specified until approved.

---

## 6. Color Coordination

Color coordination is part of the intended AI Stylist direction.

The exact ownership of color logic between:

- product metadata,
- recommendation rules,
- fashion knowledge,
- AI reasoning,

is not yet finalized.

This must remain an architecture/product decision until explicitly resolved.

---

## 7. Travel Outfit Planning

Travel-aware styling is part of the intended product direction.

Relevant context may include:

- destination,
- trip duration,
- activities,
- occasions,
- wardrobe reuse,
- outfit planning,
- shopping recommendations.

Weather integration and packing optimization are NOT automatically confirmed requirements unless separately approved.

---

## 8. Visual / 3D Direction

Strong visual outfit presentation is part of the project direction.

Advanced live 3D visualization is inside the broader project scope.

However:

Advanced 3D does NOT automatically belong to the first implementation milestone.

The following concepts must remain distinct until formally decided:

- 2D outfit composition,
- 3D avatar visualization,
- virtual try-on.

Do NOT automatically interpret "3D" as photorealistic virtual try-on.

---

## 9. Backend Technology

Confirmed backend stack:

- Java
- Spring Boot
- Microservices architecture

Microservices architecture is confirmed.

Exact service boundaries are NOT automatically confirmed.

Domain analysis and data ownership must determine appropriate service boundaries.

Agents must NOT create a new microservice merely because a business domain exists.

---

## 10. Frontend Technology

Confirmed frontend stack:

- React

Exact frontend architecture and supporting libraries remain subject to later architecture decisions.

---

## 11. AI Direction

The current project materials include an AI Stylist direction involving concepts such as:

- LLM-based interaction,
- vector search,
- Qdrant,
- Neo4j knowledge graph,
- product retrieval,
- fashion knowledge,
- product hydration.

These existing ideas must be audited during specification discovery.

They must NOT be expanded automatically.

AI recommendations must ultimately remain connected to real StyleMind product/catalog data rather than invented purchasable products.

---

## 12. Development Method

The project will be developed primarily with coding agents.

Therefore:

- approved specifications are the source of truth,
- missing requirements must not be invented,
- unresolved behavior must be marked TBD,
- scope expansion must be explicit,
- architecture changes must be explicit,
- API changes must be explicit,
- business rules must be traceable,
- agent assumptions must be distinguishable from confirmed decisions.

---

## 13. Specification Principle

The project follows this general sequence:

Discovery
â†’ Business specification
â†’ Product specification
â†’ Domain analysis
â†’ Architecture
â†’ Contracts
â†’ Security / AI specifications
â†’ Test strategy
â†’ Agent harness
â†’ Implementation

Implementation must not become the source of truth for missing business requirements.

When documentation conflicts:

the conflict must be surfaced rather than silently resolved.
