# EP-07 — Workflow và use flows

**Historical flow proposal:** Owner Prompt 08 (2026-10-29) supersedes all slot examples below for current Stylist work. The active rule is exactly one women’s TOP plus one BOTTOM per outfit; ONE_PIECE, SHOES, ACCESSORY and other slots are excluded. P03-A scope/evidence is in [its handover](EP-07_P03A_SESSION_HISTORY_REPORT.md); other flow behavior remains proposed until separately authorized.


Revision **2026-10-27**. Tất cả hành vi Stylist bên dưới là **PROPOSED — NOT APPROVED**, trừ nơi ghi OBSERVED/existing. Diagram labels dùng English; diễn giải dùng tiếng Việt. Xem [contract](EP-07_PHASE_A_CONTRACT_MEMO.md), [dependencies](EP-07_DEPENDENCY_CONTRACTS.md), [technical design](EP-07_TECHNICAL_DESIGN.md). Mũi tên thể hiện lời gọi/data flow, không phải FK xuyên database.

## 1. Workflow khách hàng

Khách đã đăng nhập mở tư vấn, nhập dịp/phong cách/ngân sách/điều cần tránh. Backend đọc profile khi được yêu cầu, tìm variant phù hợp, ghép các bộ hợp lệ, nhờ model chọn/giải thích, kiểm lại nguồn trước khi lưu kết quả. Khách xem, gửi yêu cầu mới để chỉnh lại, hoặc chủ động lưu một bộ. Tạo gợi ý không mua hàng, không trừ tồn kho, không tạo Order/Payment.

```mermaid
flowchart TD
    A[Authenticated customer] --> B[Open styling session]
    B --> C[Submit structured context]
    C --> D[Resolve permitted context]
    D --> E[Retrieve shop candidates and reviewed rules]
    E --> F[Compose hard-valid outfits]
    F --> G[AI selection and grounded reasons]
    G --> H[Recheck current source data]
    H --> I[Save and return valid results]
    I --> J{Customer choice}
    J -->|Refine| C
    J -->|Save outfit| K[Wardrobe-owned save API]
    J -->|Buy later| L[Existing Cart and Checkout flow]
```

Owned-garment context là target dependency, chưa có producer. Shop-only generation là slice có thể xây trước, **không tự thay mixed-source acceptance**. Nếu khách yêu cầu owned context mà producer chưa hỗ trợ, không lặng lẽ bỏ context đó rồi trả thành công.

## 2. Sequence kỹ thuật tổng

```mermaid
sequenceDiagram
    actor C as Customer
    participant G as Gateway
    participant S as Stylist
    participant D as Stylist DB
    participant I as Identity
    participant W as Wardrobe owner
    participant Q as Qdrant
    participant N as Neo4j
    participant P as Commerce
    participant L as Selected LLM
    C->>G: POST /stylist/api/v1/stylist/sessions/{id}/requests
    G->>S: Strip /stylist prefix, preserve request
    S->>S: JWT, role, body and session-owner checks
    opt use_style_profile
        S->>I: GET /api/v1/me/style-profile with caller bearer
        I-->>S: Profile or style_profile null
    end
    opt Explicit owned context, target capability only
        S->>W: Read permitted owned items and criteria
        W-->>S: Allowed context or denial
    end
    Note over S,W: Remote calls outside local DB transactions
    S->>D: Admission transaction: lock session, insert request
    D-->>S: Commit PROCESSING and request ID
    S->>Q: Bounded filtered candidates per slot
    Q-->>S: Candidate references and metadata evidence
    S->>N: Reviewed compatibility knowledge
    N-->>S: Approved rule IDs and facts
    S->>P: Hydrate candidate source data
    P-->>S: Current catalog views
    S->>S: Hard filters, bounded compose/rank, alias whitelist
    alt No feasible outfit
        S->>D: Final transaction: NO_RESULT, zero recommendations
    else At least one complete outfit
        S->>L: Sanitized candidate outfits, strict output schema
        L-->>S: Outfit aliases, reasons and tips
        S->>S: Strict parse and whitelist validation
        S->>P: Recheck selected variants, price and eligibility
        P-->>S: Current authoritative source data
        opt Owned references in target mixed flow
            S->>W: Revalidate source access
            W-->>S: Still allowed or revoked
        end
        S->>S: Recompute hard rules and whole-VND totals
        S->>D: Short final transaction, atomic result and items
    end
    D-->>S: Commit terminal state
    S-->>G: 201 result, or typed failure
    G-->>C: Preserve status, body and correlation ID
```

Lỗi sau admission: chuyển PROCESSING→FAILED bằng transaction ngắn khi có thể. Lỗi final write không xóa admission đã commit. Nếu DB unavailable, status có thể còn PROCESSING; không giả rollback toàn bộ request. Không giữ khóa/connection trong lúc gọi model/Feign/retrieval.

## F01 — Tạo session và xem lịch sử

**Actor/trigger:** customer mở trang tư vấn. Input POST session `{}` hoặc title; owner lấy từ JWT, không từ body. Precondition JWT hợp lệ và role cho phép. Output proposed 201 session; GET messages owner-scoped trả page lịch sử, không ghi dữ liệu.

```mermaid
flowchart TD
    A[Create session] --> B{JWT and role valid?}
    B -->|No| X[401 or 403]
    B -->|Yes| C[Validate title and unknown fields]
    C --> D[Insert session using JWT subject]
    D --> E[201 session and Location]
    F[Read messages] --> G[Owner-scoped session lookup]
    G -->|Missing or other owner| H[404 same response]
    G -->|Owned| I[Stable bounded page and next cursor]
```

Data: STYLIST_SESSION, STYLIST_MESSAGE; source-message links từ request batch lookup. Test empty history, same timestamp tie, không page trùng, A không xem B, cursor invalid/cross-session, HEAD không ghi. Không tạo close/delete/title-edit endpoints chỉ vì model có closed_at/title.

## F02 — Nhận request và resolve context

**Input:** session ID, manual context, structured budget/exclusions, optional profile, optional explicit target-owned refs. Validate local owner trước đọc private remote context. Explicit scalar/null thắng saved requirement được chỉ định; profile chỉ là soft preferences. Budget/hard exclusions phải ở structured fields, không suy từ câu chat rồi tự áp dụng.

```mermaid
flowchart TD
    A[Validate request and session ownership] --> B{Profile requested?}
    B -->|Yes| C[Read caller style profile]
    B -->|No| D[Use explicit request context]
    C -->|Null profile| D
    C -->|Timeout or malformed| X[Typed context failure, no silent fallback]
    C -->|Valid profile| D
    D --> E{Owned context requested?}
    E -->|No| F[Resolve effective context]
    E -->|Yes| G{Producer capability available and permitted?}
    G -->|No| H[Fail requested context, no fabricated owned data]
    G -->|Yes| F
    F --> I{Meaningful and consistent?}
    I -->|No| J[400, no admission write]
    I -->|Yes| K[Short admission transaction]
    K --> L[Persist PROCESSING request and allowed links]
```

Profile absent hợp lệ; profile service outage khác absent. Unknown soft code cảnh báo/ignore riêng giá trị theo contract; unknown hard code phải được xử lý rõ. Identity hiện có weight_kg, nhưng proposed data minimization không gửi cân nặng/chiều cao mặc định ra model. Saved Wardrobe requirement chưa có API/bảng physical. Test precedence/null/[]/unknown/foreign source_message, malformed profile, permission denied, no DB write on preflight error.

## F03 — Retrieval có nguồn và giới hạn

**Trigger:** context hợp lệ và request admitted. Qdrant dùng embedding tìm shop candidates theo slot; Neo4j trả tri thức đã review. Các store là projection, không quyết giá/quyền. Hydrate Commerce trước khi dùng candidate. Metadata product/category/brand hard rules cần source có đủ thuộc tính, internal batch một mình chưa đủ.

```mermaid
flowchart LR
    A[Effective context] --> B[Embed bounded query]
    B --> C[Qdrant search per required slot]
    C --> D[Hydrate Commerce references]
    E[Reviewed knowledge] --> F[Neo4j rule retrieval]
    D --> G[Candidate evidence set]
    F --> G
    G --> H[Hard filters and complete templates]
```

Không một global top-K toàn áo rồi gọi “không có quần phù hợp”. Slot mapping phải được curate; tên category không tự trở thành ontology. Không index private owned items chung với shop catalog. Empty dataset/insufficient candidates khác retrieval outage. “NO_RESULT” là không có bộ trong tập ứng viên đã xét, không chứng minh toàn shop không có bộ nào. Sample product pagination không phải consistent full export. Test Recall@K theo slot, inactive, missing metadata, draft knowledge, mismatch model/dimension và outage hai stores.

## F04 — Ghép và xếp hạng bằng code

**Input:** candidate whitelist + mapped context/rules. Templates đề xuất TOP+BOTTOM hoặc ONE_PIECE; không có optional slot. Selected owned anchors chỉ áp dụng nếu được owner duyệt và có producer đúng. Pure engine không gọi network, không ghi DB.

```mermaid
flowchart TD
    A[Group eligible candidates by slot] --> B[Build bounded complete combinations]
    B --> C[Reject source duplicates and slot conflicts]
    C --> D[Check hard exclusions and whole-VND budget]
    D --> E[Score supported soft preferences]
    E --> F[Stable ranking and diversity]
    F --> G{Any complete outfits?}
    G -->|No| H[NO_RESULT without LLM call]
    G -->|Yes| I[Freeze outfit aliases and item mapping]
```

Giá mỗi bộ là tổng SHOP_VARIANT, quantity implicit 1; OWNED_WARDROBE hợp lệ có cost 0. Shop variant nằm trong outfit đã lưu vẫn là shop item, không cost 0. Hard amount=900000 chấp nhận total=900000, reject 900001; không float/round/overflow. Optional accessories cũng tính vào budget. Soft budget chỉ rank; không tự chuyển hard. Weights/caps ở memo là đề xuất cần benchmark. Test insufficient slot, zero/exact/+1/overflow, duplicate, no combinatorial explosion, tie-break reproducible.

## F05 — LLM chọn và diễn đạt

**Input:** một tập hữu hạn outfit đã hard-valid, aliases như O1/O2 và item aliases, evidence text/rule IDs tối thiểu. LLM chỉ chọn outfit alias và tạo reasons/tips; không đổi composition, sinh product ID, giá, URL hoặc gọi tools. Code map aliases về typed IDs gốc.

```mermaid
flowchart TD
    A[Validated candidate outfits] --> B[Minimize and delimit evidence]
    B --> C[One selected model with strict JSON schema]
    C --> D{Provider outcome}
    D -->|Refusal, timeout, truncation| E[Typed failure, no paid retry]
    D -->|JSON output| F[Strict parse and size checks]
    F --> G{All aliases and reasons valid?}
    G -->|No| H[502 invalid result, save no recommendation]
    G -->|Yes| I[Map aliases to immutable source references]
```

Proposed output example (runtime-only aliases):

```json
{"outfits":[{"outfit_alias":"O1","explanation":"Phù hợp tiêu chí màu đã chọn.","styling_tips":null,"item_reasons":[{"item_alias":"I1","reason":"Khớp màu trung tính trong metadata."},{"item_alias":"I2","reason":"Bổ sung slot còn lại theo template."}]}]}
```

Schema enforce shape; validator enforce alias membership, uniqueness, complete item reasons and forbidden price/name/URL fields. Free-text factual claims vẫn cần evidence/human quality review; JSON không loại hết hallucination. Nếu provider giảm số kết quả, trả fewer theo contract, không bù bằng fabricated outfit. Không gửi email/JWT/full history/raw private images; user/catalog/knowledge text đều là untrusted data. Model choice tại technical design là đề xuất benchmark, không production approval.

## F06 — Final checks và atomic save

**Trigger:** output model parse được và aliases hợp lệ. Mọi outfit qua authoritative Commerce checks; target mixed flow còn Wardrobe access recheck. Không dựa giá Qdrant hoặc giá model trả về.

```mermaid
flowchart TD
    A[Parsed whitelisted selection] --> B[Recheck current Commerce data]
    B -->|Outage or malformed| X[Failure, zero result saves]
    B --> C{Target owned sources still permitted?}
    C -->|Revoked| Y[409 context access changed]
    C -->|Yes or no owned input| D[Recompute all hard rules and totals]
    D --> E[Remove complete outfits invalidated by source changes]
    E --> F[Lock session then request in short transaction]
    F --> G{PROCESSING and deadline still valid?}
    G -->|No| Z[Reject late finalization]
    G -->|Yes| H[Insert all valid recommendations and items]
    H --> I[Set SUCCEEDED or NO_RESULT and safe summary]
    I --> J[Commit, then return response]
```

Provider structural/whitelist violation reject **toàn output**; price/status đổi sau output hợp lệ có thể loại từng complete outfit theo memo. Ví dụ 320000+450000=770000 ban đầu; giá sau đổi thành 500000+450000=950000, hard900000 → không lưu bộ đó. Tối đa bound/deadline còn lại, không tự lặp model để chữa lỗi. Nonempty recommendation aggregate; insert item failure rollback cả result batch. Không distributed atomicity với Commerce/Wardrobe: chỉ cam kết đã check tại lần đọc, không price lock hay revoke linearizability xuyên service.

## 3. State và recovery — thuộc F06

```mermaid
stateDiagram-v2
    [*] --> PROCESSING: admission committed
    PROCESSING --> SUCCEEDED: valid results committed
    PROCESSING --> NO_RESULT: no eligible outfits committed
    PROCESSING --> FAILED: observed failure recorded
    PROCESSING --> INTERRUPTED: stale request handled on new admission
    SUCCEEDED --> [*]
    NO_RESULT --> [*]
    FAILED --> [*]
    INTERRUPTED --> [*]
```

Vocabulary này **chưa được duyệt**, chưa có Stylist migration. Runtime deadline và requested_at admission-age khác nhau; chưa có deadline column. Không resume AI sau crash để tránh bill lặp. Fault recording có thể thất bại khi DB down. GET chỉ đọc state, không sửa stale rows; new admission mới xử lý stale under lock theo proposal. Duplicate sequential POST tạo request mới; không hứa idempotency/exactly-once billing. Structured-only request bị mất response chưa có list-requests endpoint để discover chắc chắn: đây là gap, không giấu trong content.

## F07 — Đọc result và lịch sử an toàn

**Input:** owner JWT + request/recommendation ID. Local owner-join trước remote calls; nếu có owned private refs phải revalidate trước trả explanation/item names. Catalog data được hydrate hiện tại; không replay historical price.

```mermaid
flowchart LR
    A[Read recommendation] --> B[Owner-scoped local read]
    B -->|Foreign or missing| C[404]
    B -->|Owned| D[Recheck access and current source views]
    D -->|Permission changed| E[No private explanation disclosure]
    D -->|Valid| F[200 current views and stored reasons]
```

Missing/inactive shop source có availability unavailable, historical ID giữ lại, current total null nếu không tính đủ. Source outage trả typed failure, không giả inactive. Không gọi provider hoặc tạo row khi GET. Lịch sử chat chứa USER content cũng có thể nhắc đồ private: read permission policy/history redaction phải được chốt, không chỉ chặn recommendation rồi vẫn leak qua message. Test B đổi IDs, revoked source, source disappeared, GET/HEAD no writes, source counts bounded, no price-snapshot claim.

## F08 — Refine bằng request mới

Khách gửi context và hard constraints mới trong cùng session. Proposal không kế thừa âm thầm budget/constraints của request trước; client muốn giữ phải gửi lại structured values. “Rẻ hơn” trong chat chưa phải amount/hard flag. Không sửa kết quả cũ, không tự thêm parent_request_id hoặc locked-item feature.

```mermaid
flowchart LR
    A[View previous result] --> B[Submit explicit revised request]
    B --> C[Validate same-session source message]
    C --> D[Create new request ID]
    D --> E[Run normal validated pipeline]
    E --> F[Keep previous request and result unchanged]
```

Test source message từ session khác, omitted/null budget semantics, constraints full replacement, same content repeated/no-idempotency, same-session admission race. Nếu cần conversational inheritance/lineage, chốt contract/model delta trước, không dùng LLM memory tự sửa hard constraints.

## F09 — Save Outfit, share và Cart handoff

**OBSERVED:** Wardrobe POST `/api/v1/me/outfits` nhận `name`, `creation_source`, `items:[{variant_id}]`, 1–10 unique variants; caller-owned, current purchasability check. Không nhận wardrobe_id/source_recommendation_id/owned provenance/slots. creation_source=AI là label client gửi, chưa phải verified AI recommendation provenance.

Wardrobe `/me/outfits` và `/me/shared-outfits` hiện yêu cầu `ROLE_CUSTOMER`. ADMIN-only JWT có thể được cho dùng Stylist theo proposal nhưng chưa được save qua Wardrobe; không tự cấp CUSTOMER role hoặc sửa producer authorization để làm flow xanh.

```mermaid
sequenceDiagram
    actor C as Customer
    participant S as Stylist
    participant W as Wardrobe
    participant P as Commerce
    C->>S: Read own recommendation
    S-->>C: Current validated shop-variant items
    C->>W: POST /api/v1/me/outfits with chosen variant IDs
    W->>P: Existing internal batch lookup
    P-->>W: Current purchasability and display data
    W->>W: Save outfit and items atomically
    W-->>C: Created outfit
    Note over C,W: Explicit customer action, no automatic save or purchase
```

Body existing (synthetic ID):

```json
{"name":"Bộ đi làm","creation_source":"AI","items":[{"variant_id":"30000000-0000-4000-8000-000000000001"}]}
```

Đường này hỗ trợ save shop result với code hiện có; không chứng minh source recommendation link hoặc owned context đã hoàn thành. Save mixed result cần producer contract/schema khác, team owner phải duyệt. Share by email/public link dùng Wardrobe API hiện hữu sau save; Stylist không thêm friend graph/chat/notification. Buy là user chuyển variant tới Cart; không tạo Payment từ AI. Test fresh eligibility/save ownership, duplicate click behavior, rejection old/inactive variant, mixed refs không cast thành variant IDs.

## F10 — Đồng bộ catalog projection

**Trigger target:** Commerce catalog event được producer xác thực, version tăng đơn điệu theo aggregate. Hiện PaymentConfirmed delivery tồn tại nhưng không phát catalog changes. Product list paging không thay full consistent snapshot.

```mermaid
flowchart TD
    A[Commerce catalog event] --> B[Authenticate and validate envelope]
    B --> C{Newer aggregate version?}
    C -->|Duplicate or stale| D[No searchable rollback]
    C -->|New| E[Serialize complete processing per aggregate]
    E --> F{Upsert or delete?}
    F -->|Upsert| G[Build versioned embedding and payload]
    F -->|Delete| H[Keep durable tombstone and remove search data]
    G --> I[Apply Qdrant and Neo4j projections]
    H --> I
    I --> J{Both stores applied?}
    J -->|No| K[Record partial state, redeliver or reconcile]
    J -->|Yes| L[Acknowledge under approved delivery contract]
    M[Consistent snapshot and cutover watermark] --> N[Build new epoch and verify readiness]
    N --> I
```

Delayed embedding v42 không được overwrite v43. Point xóa không được làm mất high-water tombstone. Hai stores không có chung transaction; cần per-target durability/recovery và producer snapshot/cutover contract trước live R99. Transport/broker/coordinator schema chưa được duyệt; không tự thêm Kafka/RabbitMQ/inbox/outbox hoặc mượn Payment ledger. Test duplicate, same-version different-content, stale after delete, product/category/brand fanout, one-store down, restart/redelivery, epoch/rebuild race.

## 4. Dùng tài liệu này khi code

Mỗi flow phải map task → approved rule → DTO/model field → owner/transaction/side effect → negative/concurrency tests → evidence level ở [verification matrix](EP-07_VERIFICATION_MATRIX.md). Đồ thị là thiết kế đề xuất, không bằng chứng code chạy. Thay đổi business rule phải cập nhật contract/flow/tests cùng slice; chỉ đổi sơ đồ không phải approval.

## Wave 2 runtime sequence — implementation scope (2026-11-05)

This sequence records the approved generation/read slice. Owned context, Identity profile, catalog events, production Commerce policy and live-provider calls are not part of the implemented flow.

```mermaid
sequenceDiagram
    actor C as Customer
    participant G as Gateway
    participant S as Stylist API
    participant DB as Stylist PostgreSQL
    participant E as Embedding adapter
    participant Q as Runtime Qdrant
    participant P as Commerce Feign
    participant N as Reviewed Neo4j
    participant R as P02 outfit engine
    participant L as Structured OpenAI adapter
    C->>G: POST /stylist/api/v1/stylist/requests/{id}/generate {}
    G->>S: Strip /stylist and forward request
    S->>S: Validate JWT owner, route and strict empty body
    S->>DB: Read owner/status/session and verify runtime readiness
    S->>DB: Short claim transaction RECEIVED → PROCESSING
    Note over S,L: Embedding, Qdrant, Commerce, Neo4j and model calls run outside DB transactions
    S->>DB: Load manual request and USER-message context
    S->>E: Embed minimized context once
    E-->>S: 1536-dimensional vector
    par Required slot retrieval
        S->>Q: Search reviewed WOMEN TOP categories
        Q-->>S: Bounded TOP candidates
    and
        S->>Q: Search reviewed WOMEN BOTTOM categories
        Q-->>S: Bounded BOTTOM candidates
    end
    S->>P: Hydrate current candidate data
    P-->>S: Current variants and prices
    S->>N: Read approved, non-revoked scoped facts
    N-->>S: Bounded facts and provenance
    S->>R: Hard filter, compose, score and freeze aliases
    alt No complete valid outfit
        S->>DB: Atomic NO_RESULT finalization
    else Frozen whitelist exists
        S->>L: Native strict schema with complete outfit aliases
        L-->>S: Selected aliases and grounded text
        S->>S: Validate schema, aliases and item reasons
        S->>P: Recheck selected current variants and prices
        P-->>S: Current authoritative values
        S->>R: Recheck slots, exclusions and exact VND budget
        S->>DB: Atomic recommendation/items/SUCCEEDED finalization
    end
    S-->>G: Direct response DTO or safe Problem Details
    G-->>C: Response with no-store
```

Any error after claim is recorded as `FAILED` in a separate short transaction when storage is available. If the DB cannot record failure, `PROCESSING` may remain; there is no automatic recovery worker or startup reset.
