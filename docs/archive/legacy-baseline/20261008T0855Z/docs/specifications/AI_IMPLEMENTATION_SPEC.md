# StyleMind V1 AI Implementation Specification

Status: IMPLEMENTATION BASELINE

Authority: DEC-029, DEC-035, DEC-036 and the approved SAD.

## Pipeline

`User Request + User Context + Wardrobe + authoritative Catalog filters + Qdrant retrieval + Neo4j fashion relationships -> structured model context -> LlmClient -> structured OutfitCandidate -> deterministic validation/grounding -> ValidatedOutfitResult`

## Rules

1. `LlmClient` is an internal provider abstraction; domain logic does not depend on a provider SDK.
2. LLM output is never authoritative for Product, Variant, price, stock or availability.
3. Every purchasable recommendation must resolve to an authoritative Catalog Product/Variant.
4. Unresolved, inactive or invalid purchasable recommendations are suppressed.
5. Hard constraints are checked deterministically after model output and take precedence over aesthetic preference.
6. Owned Wardrobe provenance and Catalog provenance remain distinct in the result.
7. Qdrant is an index/retrieval layer, not product truth.
8. Neo4j supplies fashion relationships, not transactional product storage.
9. Provider/model configuration is environment-driven. A development stub is used when credentials are unavailable.

## Initial interfaces

- `LlmClient`: structured prompt/context in, structured candidate out.
- `CatalogGroundingClient`: resolves candidate product/variant identifiers against Catalog.
- `FashionKnowledgeClient`: reads Neo4j-backed relationships through an adapter.
- `VectorRetrievalClient`: reads Qdrant candidates through an adapter.

Freshness, deletion synchronization, provider choice, latency/cost targets and evaluation thresholds remain explicit follow-up work.
