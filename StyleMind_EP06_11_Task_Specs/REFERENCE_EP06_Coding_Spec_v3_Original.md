# STYLEMIND — EP-06 FRIENDS, WARDROBE SHARING & CHAT

**Implementation-ready specification for coding AI**  
**Version:** 3.0 (Spring Boot / PostgreSQL / Docker, proposed technical decisions)  
**Status:** Business rules approved by project owner; repository-specific integration and unresolved task conflicts still require verification.  
**Target:** Existing `wardrobe-community-service` (one Spring Boot microservice, three logical modules).  
**Scope:** US-06.01 Manage Friends, US-06.02 Share Wardrobe, US-06.03 Chat with Friends.

> **READ THIS FIRST — source-of-truth precedence**
>
> 1. The business decisions in this document supersede the old *StyleMind Functional Requirements* sharing section **FR-91–FR-102** where contradictory: **share the entire My Wardrobe**, not a single Outfit, for the new EP-06 functionality.
> 2. Do not delete or change existing Outfit Sharing APIs without explicit approval. If present, keep them separate and compatible until the team confirms whether the new Wardrobe Sharing replaces them.
> 3. **There is NO bidirectional Facebook-style friendship.** “Manage Friends” means managing the email recipients with access to the owner's Wardrobe. No friend requests, ACCEPT/REJECT, block, mutual friendship, or social graph.
> 4. Original task `Friendship migration with unique customer pairs` **conflicts** with approved business rules, especially sharing with email addresses that do not have registered Customer records. **Flag it as a requirements change requiring team approval; do not invent a friendship feature or falsely mark that original task completed.** Implement the approved email-share relationship as described, subject to approval.
> 5. The coding agent **must inspect the actual repository** (pom/build.gradle, entities, migration tools, security, Dockerfile/Compose, existing API conventions and integration points) before editing. Do not create duplicate Wardrobe/Outfit entities, databases, services or endpoints. Do not assume the below endpoint/table names already exist.

---

## 1. Business goal and precise scope

StyleMind sells women's clothing, and each Customer owns exactly one My Wardrobe containing multiple Outfits, each with catalog products. An Owner can share **all current and future Outfits** in their Wardrobe, without giving edit privileges. People invited by email may exchange private 1:1 text messages with the Owner about that Wardrobe. This is **not** general-purpose Messenger chat, social friends, a public chatroom, group chat, or AI chat. AI stylist chat history remains private.

**Approved rules:**

- Wardrobe `PRIVATE`: only Owner views and manages the Wardrobe. Previously invited email viewers cannot view or use the associated chat. Share grants and historical messages are preserved, **not revoked**.
- Wardrobe `PROTECTED`: Owner and recipients with an **ACTIVE email sharing grant** may view the whole Wardrobe. Invited recipient must sign in with that same **verified** email address. Active invitees can chat privately 1:1 with the Owner. Others cannot view/chat even if they have a copied invitation link.
- Wardrobe `PUBLIC`: anyone with a valid Public Link can **view only** the Wardrobe, including without signing in. Anyone who **also has an ACTIVE email sharing grant**, is signed in with the verified email and is a chat participant, can chat with the Owner. Merely holding a Public Link never gives chat rights.
- Owner invites by entering `Email được chia sẻ`. Send notification email containing a direct Wardrobe link. The email **need not be registered** when invited; recipient can later register, verify the exact invited email, sign in and access if the grant remains ACTIVE and the visibility permits.
- Owner sees a **People with Access / Manage Friends** list of invited emails and can remove an email individually (`REVOKED`). This never deletes their Customer account or messages and never affects other invitees. Removal blocks email-based private access and chat; if Wardrobe is PUBLIC, revoked person can still view as a public visitor.
- If Owner later shares again with the **same email**, reactivate the existing grant. A verified recipient who is the **same Customer identity** resumes their existing 1:1 chat and message history. Do not duplicate the session.
- Visibility changes and explicit revoke are **distinct operations**. `PROTECTED -> PRIVATE -> PROTECTED` restores access/chat for still-ACTIVE grants automatically; explicit REVOKED grants stay revoked.
- Owner has a **Messages** screen to list and reply to conversations. Invitees have **Chat với chủ tủ đồ** on accessible shared Wardrobe. Each Owner–recipient conversation is private to the two participants; a recipient cannot view any other recipient's chat.
- Viewer cannot rename/edit/delete any Wardrobe or Outfit, run AI alterations for Owner, see private account data (height/weight/address/cart/orders/payments), or read AI Stylist chat history.

**Authorization matrix (permission from new Wardrobe Sharing only):**

| Wardrobe state | Owner | ACTIVE invited viewer (verified sign-in) | Public-link-only viewer | REVOKED invitee |
|---|---|---|---|---|
| PRIVATE | Full Wardrobe management; see own message history | No Wardrobe access; no chat | No view/chat | No Wardrobe access/chat |
| PROTECTED | Full | View Wardrobe + private 1:1 chat | No view/chat | No view/chat |
| PUBLIC | Full | View Wardrobe + private 1:1 chat | View Wardrobe only | Public view only, no chat |

Owner viewing old chat history in PRIVATE or after revoke is a **safe technical default proposed here**, not yet separately approved by the user. Do not enable Owner to send new messages to a suspended/revoked recipient; allow read-only history only.

---

## 2. Existing system and microservice boundary

Repository services observed from screenshot:

```text
commerce-service/
identity-service/
notification-service/
payment-service/
stylist-service/
wardrobe-community-service/
.env
README.md
```

**Confirmed technologies:** Java Spring Boot, PostgreSQL, Microservices, Docker. The Java version, Spring Boot version, build tool, migration framework, message broker and security patterns have **not** been verified from source.

**EP-06 implementation boundary:** Keep all three logical modules in the existing `wardrobe-community-service`, **one application Docker image**, not new `friendship-service` and not new `chat-service`:

- `wardrobe/sharing`: Wardrobe visibility, email grants, people-with-access, public link, sharing-related resource access.
- `chat`: ChatSession, ChatParticipant, ChatMessage, order, idempotency, message-history.
- `security`: consistent authentication and authorization helpers used across Wardrobe/Outfit/Chat endpoints.

**Integrations:**

- `identity-service` owns accounts/verified emails/JWT. `wardrobe-community-service` must not query Identity's PostgreSQL schema directly. Reuse existing JWT verification and any verified-email lookup/claims contract; if these are unavailable, **report the missing contract rather than assuming unverified emails are trusted**.
- `notification-service` owns outgoing invitation email. Reuse its existing HTTP/event delivery interface; do not directly recreate email infrastructure inside Wardrobe. If asynchronous processing is already present, reuse its existing transport. No new RabbitMQ/Kafka required without need/approval.
- `commerce-service` owns catalog/product/variant truth where applicable. Preserve ACTIVE product/variant rules. Public catalog images are not automatically private resources, but private Wardrobe membership, Outfit metadata and private media must be authorization-protected.
- `stylist-service` owns AI styling. This human-to-human chat must never expose its conversation/history.
- `payment-service` unchanged.

**Service-owned data:** `wardrobe-community-service` owns share/chat schema in its existing PostgreSQL database; do not connect to other services' databases. Reuse existing Wardrobe/Outfit data model where available. If Wardrobe/Outfit storage is elsewhere, **stop and report ownership conflict** before adding competing tables.

---

## 3. Business flows (implement and test)

### F1. Change visibility

1. Owner signs in -> opens My Wardrobe Settings -> selects `PRIVATE`, `PROTECTED` or `PUBLIC`.
2. Backend resolves Owner from verified security principal and checks ownership.
3. Persist visibility change atomically; API responds with current visibility.
4. Every subsequent Wardrobe/Outfit/Item/Chat authorization reevaluates the persisted visibility. No permission-cache grants that survive a change.
5. On `PRIVATE`, email grant records and ChatSession/ChatMessage are **untouched**. On return to `PROTECTED`, ACTIVE invitees regain access/chat; REVOKED invitees do not.
6. On `PUBLIC`, valid public view works; ACTIVE email recipients retain chat. When leaving `PUBLIC`, anonymous/public view ceases immediately.

### F2. Share by email (registered or unregistered)

1. Owner enters target email -> submit share.
2. Validate Owner, email format, normalized email, no self-share; locate existing grant for `(wardrobe, normalizedEmail)`.
3. Create ACTIVE grant if absent, reactivate if REVOKED, or return a defined no-op/conflict for ACTIVE (must not create duplicate).
4. Register an invitation delivery request with reliable retry according to repo infrastructure. Return **queued/accepted** vs **delivered** honestly.
5. Notification Service emails a link **to the whole Wardrobe**, not an individual Outfit.
6. Recipient clicks -> if not registered, registers with that email and verifies it; otherwise signs in. Backend matches **server-verified account email** to ACTIVE grant and checks current visibility. Forwarding invitation link to someone with another email does not grant protected access.
7. If invite was revoked before recipient registered/logged in, access is denied.

### F3. Manage sharing / revoke / re-share

1. Owner opens People with Access; sees invited emails and ACTIVE/REVOKED state (or active list with optional history).
2. Remove a recipient -> change only its grant to REVOKED. Block their private view, send/read chat, and future invite-based access. **Keep historic session/messages**.
3. Wardrobe PUBLIC: person may still view via public link as anyone else, **cannot chat** without ACTIVE grant.
4. Owner shares same normalized email again -> reactivates the same grant and sends another email. Same verified Customer identity can reopen the original chat and old history; do not create duplicates.

### F4. Public link

1. Owner chooses PUBLIC and obtains/copies a public URL to the whole Wardrobe.
2. Any holder (Guest allowed) opens URL -> verify public link validity **and** current Wardrobe visibility == PUBLIC -> return **safe read-only view** of Wardrobe and nested Outfits.
3. Public viewer has no chat unless separately authenticated with an ACTIVE email share.
4. Leaving PUBLIC immediately disables public access (including previously copied links); token lifecycle/rotation details are an **open decision** in §11.

### F5. Open Chat (1:1)

1. Authenticated verified invited viewer opens accessible Wardrobe -> clicks "Chat với chủ tủ đồ".
2. Backend checks: Wardrobe belongs to Owner, Visibility in {PROTECTED, PUBLIC}, normalized authenticated verified email has ACTIVE grant; user is not Owner.
3. Find or create one ChatSession for `(wardrobeId, viewerCustomerId)`; add exactly Owner and Viewer as participants; return existing session on repeat/concurrent opens.
4. Display message history (ordered + paginated). Another recipient's session cannot be inspected.
5. Owner has Messages list and can open their own sessions without browsing the Wardrobe.

### F6. Send message

1. Sender signs in, opens their ChatSession, submits `clientMessageId` and nonempty text.
2. Validate sender belongs to ChatSession and current share/visibility still authorizes Chat. For Owner sending, target recipient must still have ACTIVE grant and Wardrobe must be PROTECTED or PUBLIC.
3. Deduplicate client retries by `(chatSessionId, senderCustomerId, clientMessageId)`. If same key with different payload, return conflict, do not replace previous content.
4. Within transaction, allocate a monotonic `sequenceNumber` unique per ChatSession and save message. Return persisted message, use server timestamp.
5. A retry of the same exact request returns the original message without inserting a duplicate. Two separately initiated identical-text messages with distinct keys are both valid.

### F7. Read messages / revoke during active chat

1. Authenticate participant; for Viewer reevaluate share/visibility before fetching history. For Owner, allow read-only historical access (proposed default).
2. Return only messages from requested session; deterministic sorting by `(sequenceNumber ASC)` and cursor-based pagination.
3. Revoke or PRIVATE during an open chat must block **subsequent** viewer read/send requests, not just hide Chat buttons in UI. Clear active client UI appropriately.
4. No ChatSession/ChatMessage deletion on revoke or visibility change. Re-sharing reopens same session/history.

---

## 4. Domain & PostgreSQL migration specification

**Migration rule:** Inspect existing migration tool first. Use current Flyway/Liquibase convention; create forward-only, versioned migration. Do not enable `ddl-auto=create` or `create-drop` in deployed environments. Preserve existing rows.

### 4.1. Existing `wardrobe` record

Reuse actual existing table and ID types. Add `visibility` with allowed values `PRIVATE`, `PROTECTED`, `PUBLIC`; proposed default `PRIVATE`, backfill existing Wardrobes. Ensure one Wardrobe per Owner remains enforced by the existing model.

### 4.2. `wardrobe_shares`

| Column | Meaning / constraint |
|---|---|
| `id` | PK, reuse existing ID strategy (UUID/BIGINT) |
| `wardrobe_id` | NOT NULL, FK to service-owned Wardrobe |
| `recipient_email_normalized` | NOT NULL, max 320, trimmed/lowercase by established Identity policy |
| `recipient_email_display` | Optional original normalized display |
| `recipient_customer_id` | NULL until verified registration, reference by ID only (no cross-service DB FK) |
| `status` | `ACTIVE` or `REVOKED`, NOT NULL |
| `created_at`, `updated_at` | UTC timestamps |
| `revoked_at` | nullable; reset on activation |

**Required unique key:** `(wardrobe_id, recipient_email_normalized)` (one logical share per invited email per Wardrobe). Unique is for email grants, **not** a replacement for the old, conflicting `Friendship(customerA,customerB)` requirement. Add indexes for `wardrobe_id,status`, optionally `recipient_customer_id,status`. Enforce self-share prevention and owner-only management in service layer. PostgreSQL transaction/unique violation handling must prevent duplicates under concurrent requests.

Normalize email consistently with Identity; do **not** apply provider-specific Gmail dot/plus rewriting. Trust only **verified** account emails. If an account changes email, reevaluate/share binding; do not let a stale Customer ID bypass email-grant identity checks. The original ChatSession belongs to the same stable Customer ID if re-shared, not simply anyone who later takes over the email.

**Do not create** Facebook-style `Friendship` schema/statuses. Flag conflict in implementation report for task owner approval.

### 4.3. Public-link storage (minimal proposal)

Can be stored as a column on Wardrobe or a related table depending on existing code. Use an unpredictable cryptographically random token (at least 128 bits). Provide owner-only API to generate/copy/rotate or disable as agreed. Token grants **public VIEW only while visibility=PUBLIC**; never grants chat or edit. Avoid token logging, enforce rate limiting where appropriate. See §11 for unconfirmed reactivation/rotation behavior.

### 4.4. `chat_sessions`

| Column | Meaning / constraint |
|---|---|
| `id` | PK |
| `wardrobe_id` | NOT NULL local Wardrobe FK |
| `owner_customer_id` | NOT NULL external Identity ID |
| `viewer_customer_id` | NOT NULL external Identity ID |
| `next_sequence_number` | NOT NULL, monotonically increased for message ordering |
| `created_at`, `updated_at` | UTC timestamps |

UNIQUE `(wardrobe_id, viewer_customer_id)`. The owner is resolved from Wardrobe DB, not supplied/trusted from request. No session created for a recipient without a registered, verified Customer account. Keep row on revoke.

### 4.5. `chat_participants`

Columns: `id`, `chat_session_id` local FK, `customer_id`, `joined_at`. UNIQUE `(chat_session_id, customer_id)`. Session contains exactly Owner and Viewer. Participant existence is necessary but **not sufficient** for chat authorization: also check ACTIVE share and current visibility.

### 4.6. `chat_messages`

| Column | Meaning / constraint |
|---|---|
| `id` | PK |
| `chat_session_id` | NOT NULL local FK |
| `sender_customer_id` | NOT NULL |
| `client_message_id` | NOT NULL, idempotency key provided by client |
| `sequence_number` | NOT NULL, ordered within session |
| `content` | NOT NULL text, enforce trim, nonempty, max length (proposed: 4,000 chars) |
| `created_at` | Server timestamp UTC |

UNIQUE `(chat_session_id, sequence_number)` and UNIQUE `(chat_session_id, sender_customer_id, client_message_id)`. Index `(chat_session_id, sequence_number)` for history. Use transaction and pessimistic lock or equivalent on ChatSession counter for safe concurrent writes.

**Authorization concurrency:** Race-sensitive operations should be serialized safely or revalidated at transaction commit. One straightforward MVP pattern within the same service/DB: acquire locks in a consistent order (Wardrobe -> share grant -> ChatSession), check authorization, then create message; visibility change/revoke obtains related locks in matching order. Requests committed **after a completed revoke** must not create newly authorized messages. Explain implemented race behavior in tests. Avoid unnecessarily locking during slow email/network calls.

### 4.7. Data integrity / retention

- No cascade delete of chat history on grant revoke or PRIVATE visibility.
- No duplicate ChatSessions for same Wardrobe/Viewer, including concurrent requests.
- No database joins/foreign keys across microservices.
- Migrations must be safe on existing data (backfill, nonnull constraints, duplicates detection).

---

## 5. Public REST API contract (proposed; adapt to actual conventions)

Base path: `/api/v1`. Use actual response envelope, ID format, pagination and validation patterns already in the repo. Never take acting `ownerId` or `senderId` from client request body; obtain from authenticated principal.

### 5.1. Wardrobe / Sharing

| HTTP | Path | Access | Result |
|---|---|---|---|
| GET | `/wardrobes/me` | Owner | Own Wardrobe view |
| PATCH | `/wardrobes/me/visibility` | Owner | Change visibility |
| GET | `/wardrobes/me/shares` | Owner | Paged ACTIVE (and optional REVOKED) email grants |
| POST | `/wardrobes/me/shares` | Owner | Create/reactivate email share; queue invite |
| DELETE | `/wardrobes/me/shares/{shareId}` | Owner | Revoke specific grant; idempotent 204 allowed |
| GET | `/wardrobes/{wardrobeId}` | Owner / authorized Email Viewer | Safe view / owner view as appropriate |
| GET | `/wardrobes/{wardrobeId}/outfits` | Owner / authorized Email Viewer | Protected Outfit list |
| GET | `/public/wardrobes/{token}` | Anyone with token while PUBLIC | Public read-only Wardrobe |
| POST | `/wardrobes/me/public-link` | Owner | Generate or rotate link (see open decision) |
| GET | `/wardrobes/me/public-link` | Owner | Get link info according to chosen token storage |
| DELETE | `/wardrobes/me/public-link` | Owner | Invalidate link (may also require visibility change) |

**PATCH visibility — request:** `{"visibility":"PROTECTED"}`. Response `200` containing Wardrobe ID, new visibility.

**POST share — request:** `{"email":"mai@example.com"}`. Example response `201/200`:

```json
{
  "shareId": "share-id",
  "wardrobeId": "wardrobe-id",
  "recipientEmail": "mai@example.com",
  "status": "ACTIVE",
  "permission": "VIEW",
  "notificationStatus": "QUEUED"
}
```

If invite dispatch is asynchronous, `QUEUED` must **not** be described as sent/delivered. Existing ACTIVE share must not create duplicate row; return 200/no-op or 409 following team convention. Re-share from REVOKED activates same row and may queue a new email. `GET shares` must return only the Owner's data and not expose unrelated Customer private profile data.

**Protected shared Wardrobe:** Validate identity via Identity contract; compare the server-verified account email to ACTIVE invite; `401` when authentication required but missing; `403` or privacy-preserving `404` on denial. All Outfit/Item/private-media endpoints must share the same authorization logic. Public DTO excludes private User/AI fields.

**Email invitation URL:** Links to *Wardrobe*, e.g. `{frontendBaseUrl}/wardrobes/{wardrobeId}/shared` (actual client path TBD). Link itself is not a protected bearer permission; identity+verified email+ACTIVE share+visibility are necessary for protected access. Invitation can be forwarded without granting someone else's rights.

### 5.2. Chat

| HTTP | Path | Access | Result |
|---|---|---|---|
| POST | `/wardrobes/{wardrobeId}/chat` | Verified ACTIVE invitee | Find/create own 1:1 session |
| GET | `/chats` | Authenticated Owner/invitee | Own permitted chat metadata; Owner may include suspended sessions as read-only |
| GET | `/chats/{chatId}/messages?beforeSequence=&limit=` | Authorized participant | Cursor page, ordered |
| POST | `/chats/{chatId}/messages` | Authorized active participant | Persist text message idempotently |

**POST message — request:**

```json
{"clientMessageId":"ea7e7d2b-1cc6-46c9-a96d-25ed01ac335c","content":"Bạn thấy Outfit này thế nào?"}
```

**Response example:**

```json
{"messageId":"message-id","chatId":"chat-id","senderCustomerId":"customer-id","sequenceNumber":15,"content":"Bạn thấy Outfit này thế nào?","createdAt":"2026-10-13T03:00:00Z"}
```

`GET messages`: return ordered messages, page limit (proposed default 30, max 100), `nextCursor` based on sequence; do not trust client-provided sender or recipient. For identical retry id+content return prior message, for same id with different content `409 Conflict`.

**Standard errors:** `400` malformed input/self-share/empty text, `401` unauthenticated, `403` forbidden, `404` missing/non-disclosable resource, `409` conflict/idempotency violation, `429` abuse/rate limits, `503` dependency unavailable. Reuse project's error response format.

### 5.3. Notification integration contract

Share operation triggers email containing:

- Safe display name of Owner.
- Notification that Owner shared **My Wardrobe** (not an individual Outfit).
- Direct Wardrobe URL.
- Sign-in/register instruction for recipient.

Keep `notification-service` responsible for delivery, retries and templates. If repo already uses events/queue, reuse it. If only HTTP, integrate with its existing endpoint and use an outbox/retry approach if supported; do not block/share rollback due to slow external SMTP without a deliberate design. Ensure duplicate delivery is minimized and state is not misreported. Prefer existing infrastructure to introducing a broker. For notification failures, owner-facing status must distinguish share stored vs email delivered.

---

## 6. Authorization and privacy, central rules

One consistent backend policy for all resources:

```text
canViewWardrobe(requester, wardrobe):
    if requester == owner: allow
    if visibility == PRIVATE: deny
    if visibility == PUBLIC and valid-public-route/token: allow READ_ONLY
    if visibility == PROTECTED:
        require login and verifiedEmail
        require matching ACTIVE share
        allow READ_ONLY
    otherwise deny

canChat(requester, session):
    require logged-in, verified identity, session membership
    require Wardrobe visibility in {PROTECTED, PUBLIC}
    require ACTIVE share for session.viewer (same verified email + stable Customer ID)
    require sender is owner or viewer
    allow
```

**Important nuance:** An authenticated ACTIVE email invitee can view Wardrobe in PUBLIC through normal protected/shared Wardrobe flow even without a public token. Public-only visitor cannot gain email-chat rights. Owner chat history read-only may be allowed when share is revoked/visibility PRIVATE, subject to business confirmation.

Apply checks to direct resource URLs, including Wardrobe, Outfit, OutfitItem, membership information, private images and human shared-chat context. No private Owner height/weight, addresses, orders, cart, payment, AI history or AI feedback in shared DTO. UI hiding a button is not security.

Image URLs: catalog product images already public may remain public; any Wardrobe-owned private media must be served through authorized access or suitably short-lived signed URLs. Set appropriate cache controls for protected data. Tokens should not be logged.

Use Spring Security to verify JWT per existing project convention, derive principal IDs/email from verified security context. For internal Identity or Notification calls, reuse secure service-to-service authentication. Treat service failure as **fail closed** for protected access/chat rather than allowing anonymous fallback.

---

## 7. Implementation layers and coding conventions

Within **existing** `wardrobe-community-service`, organize conceptually (adapt to actual package structure):

```text
wardrobe/
  application/ domain/ infrastructure/ api/
sharing/
  application/ domain/ infrastructure/ api/
chat/
  application/ domain/ infrastructure/ api/
security/
integration/
  identity/ notification/
```

For each module use existing Spring conventions: Controller (thin), DTO + Bean Validation, Service with `@Transactional` where needed, Repository/Spring Data JPA, Entity, mapper, error handling, integration client. Use Spring Security method/resource authorization. Avoid copy/paste authorization checks between controllers; centralize in service/policy methods.

Use existing Java/Spring Boot versions and Maven/Gradle conventions found in repo. Do not introduce Lombok, MapStruct, Flyway, Redis, Kafka, WebSocket, Feign or new libraries without examining existing tools and a specific need.

**MVP transport recommendation (not previously approved):** implement reliable REST send/history first, make frontend real-time/polling/WebSocket an explicit follow-up once agreed. Do not claim WebSocket complete unless actually implemented and tested. Text-only Chat is a proposed minimal default.

---

## 8. Docker / PostgreSQL deployment requirements

- Keep existing **six** named microservices; EP-06 is added to `wardrobe-community-service`, not a new service/container.
- Update only this service's Dockerfile/build and the root Compose service configuration **if needed**. Inspect existing `docker-compose.yml` or deployment scripts first; do not overwrite shared configuration.
- New entities/migrations deploy to the PostgreSQL DB/schema owned by `wardrobe-community-service`. DB must be persistent via existing volumes; no destructive `ddl-auto` on startup.
- Use Docker service DNS (`identity-service`, `notification-service`, DB service name) rather than localhost inside containers. Use healthchecks/readiness and environment-driven URLs and secrets.
- Do not expose PostgreSQL or private inter-service endpoints unnecessarily to the host/public network. Do not put API keys/passwords in committed source, Compose or logs.
- Migration is versioned and runs once per deployment by the existing mechanism. Handle PostgreSQL readiness, startup ordering and retry.
- Logs should have request/correlation IDs but redact JWT, public tokens, recipient-sensitive contents and any private message text unless explicitly needed under approved logging policy.

**Docker acceptance checks:** `docker compose config`, build relevant service, start DB & service, migration executes, health endpoint responds, REST integration tests pass, restart service+DB retains share/chat records. Perform only commands appropriate to actual repo; do not assume Compose filename, DB name or ports.

---

## 9. Automated test & acceptance specification

**Tests:** unit tests for state/policy; Spring MVC/Security tests for endpoints; integration tests with real PostgreSQL (prefer Testcontainers when already available), and Docker smoke tests. Mock external Identity/Notification unless an integration test explicitly covers them.

| ID | Case | Expected |
|---|---|---|
| MF-01 | Owner lists grants | Only own recipients; pagination |
| MF-02 | Self-share | Reject; no data created |
| MF-03 | Duplicate same email / case variants | Exactly one logical grant |
| MF-04 | Concurrent share requests | DB unique constraint prevents duplicate |
| MF-05 | Other customer revokes grant | Denied; unchanged |
| MF-06 | Revoke one of several grants | Only that recipient REVOKED |
| MF-07 | Re-share same email | Same grant reactivated; old chat retained |
| WS-01 | PRIVATE / nonowner / guest | Deny all protected view/chat |
| WS-02 | PROTECTED + ACTIVE verified email | Allow Wardrobe/Outfit VIEW and own Chat |
| WS-03 | PROTECTED + copied invite link + wrong email | Deny protected access |
| WS-04 | PUBLIC + valid link guest | View allowed; Chat denied |
| WS-05 | PUBLIC + ACTIVE email invitee | View + own 1:1 Chat |
| WS-06 | PROTECTED->PRIVATE->PROTECTED | ACTIVE restores; REVOKED does not |
| WS-07 | Revoke while PUBLIC | Viewer remains public VIEW only, no Chat |
| WS-08 | Invite unregistered, register+verify later | Access after verified signup if still ACTIVE |
| WS-09 | Invite unregistered, revoke before signup | Deny after signup |
| WS-10 | Direct Outfit/Item/private media endpoints | Cannot bypass Wardrobe authorization |
| WS-11 | Shared DTO response | No height/weight/address/AI chat/payment data |
| WS-12 | Public token invalid / visibility not PUBLIC | Deny public route |
| CH-01 | Authorized invitee opens Chat twice | Same Session, exactly two participants |
| CH-02 | Concurrent Chat opens | One Session only |
| CH-03 | Outsider reads/sends to guessed chatId | Denied |
| CH-04 | Invitee A reads Invitee B's chat | Denied |
| CH-05 | Valid send and history | Saved + ordered with stable sequence |
| CH-06 | Duplicate retry ID + same body | One row, original response |
| CH-07 | Duplicate retry ID + different body | 409 Conflict |
| CH-08 | Concurrent message sends | Unique, monotonic sequence per Session |
| CH-09 | Revoke while chat open | Subsequent reads/sends denied for Viewer |
| CH-10 | Re-share | Old Session and full history available to same Customer |
| CH-11 | PRIVATE -> PROTECTED | Chat suspended then resumed |
| CH-12 | Public-only visitor tries to start Chat | Denied |
| INT-01 | Identity unavailable during protected access | Fail closed; meaningful error |
| INT-02 | Notification email failure | Grant kept as decided, status truthful, delivery retriable |
| INT-03 | Restart Docker service/Postgres | Data persists; migrations safe |
| INT-04 | Simultaneous revoke and send | Defined transaction ordering; no post-revoke unauthorized insert |

Original test `removed friendships` is not applicable because there is no friendship lifecycle. Replace with `revoked email-share` tests **only after task-owner approval**; report the difference.

---

## 10. Definition of Done & deliverables for the coding agent

**Deliverables:**

1. Inventory of existing project components reused/changed, including located Wardrobe/Outfit ownership, JWT verified-email contract, notification contract, build/migration tools, Compose setup.
2. A short written implementation plan and a **clear warning about Friendship task mismatch**, before touching data schema.
3. Versioned PostgreSQL migrations (Share/Visibility, Chat tables, necessary unique/index/check constraints; optional public-token/outbox according to chosen implementation), compatible with existing schema.
4. Spring Boot domain/repositories/services/controllers/DTO/security + external integration adapters, all **inside `wardrobe-community-service`**.
5. Owner and recipient flows, Public Link views, Chat REST session/send/history functionality, authorization on all nested Wardrobe endpoints; keep AI chat separate.
6. Automated tests matching §9, covering security, duplicate, concurrent, revoked, PRIVATE/Public/Protected cases.
7. Docker adjustments only as required and checked; README with configuration, migration, start/test commands, cURL or Postman examples.
8. Final report: files changed, API list, migrations, test/build/docker evidence, assumptions, outstanding decisions, known gaps. **Never claim tests passed if they were not run.**

**Completion criteria:** No duplicate email share/session, no self-share, no unauthorized modifications, no public-only Chat, email never bypasses verified identity, private data cannot be fetched by direct resource IDs, reversible visibility suspension, durable history across revoke/re-share, and service starts with existing Docker deployment.

---

## 11. OPEN DECISIONS (record, don't silently invent requirements)

These were not decided in the conversation and require team confirmation. The safe defaults below let a coding agent propose an MVP, **not** declare them previously approved:

| Decision | Proposed implementation default | Status |
|---|---|---|
| O-01 Original Friendship migration task | Implement `WardrobeShare`, document conflict; do not create social friendship | **BLOCKER for task acceptance**; team sign-off required |
| O-02 Existing per-Outfit sharing | Preserve existing feature unmodified; new Wardrobe sharing independent | Confirm replacement vs coexistence |
| O-03 Can Owner invite while PRIVATE? | Disallow new share API while PRIVATE with clear error; existing grants remain saved | Confirm |
| O-04 Public link lifecycle | Random token; invalidate old token on explicit rotate; refuse public access unless PUBLIC; on re-enable PUBLIC issue new token if previously disabled | Confirm |
| O-05 Chat transport | REST MVP; WebSocket only after explicit agreement | Confirm |
| O-06 Chat message types | Text-only MVP, 4,000-char max | Confirm |
| O-07 Owner viewing suspended history | Allow owner read-only; no sends while share inactive/PRIVATE | Confirm |
| O-08 Exact framework/toolchain | Follow actual repo Java/Spring/ORM/migrations/Compose, don't assume versions | Inspect source |
| O-09 Email delivery infrastructure | Reuse notification-service existing API/events, reliable retry pattern | Inspect source |

If coding now without further replies: implement only approved behaviors plus explicitly labeled safe defaults; **stop before irreversible changes or fake Friendship acceptance**. Preserve old code where compatibility is uncertain.

---

## 12. Direct instructions to AI coding agent

> You are implementing EP-06 inside the **existing** StyleMind repository. Read this specification fully. First inspect the real `wardrobe-community-service`, the existing `identity-service`/`notification-service` public integration contracts, migrations, auth configuration and Docker setup. Report any discrepancy. Implement **all EP-06 sharing and chat business logic as logical modules in the one existing `wardrobe-community-service` Spring Boot application** with PostgreSQL and existing Docker deployment; do not create chat-service/friendship-service. There is **no two-way friendship**; Manage Friends is the list of email Wardrobe shares. Do not fabricate a Friendship lifecycle; flag the original Friendship migration requirement for approval. Do not modify unrelated services, overwrite old sharing, or duplicate Wardrobe/Outfit entities. Write migration(s), secure APIs, DTOs, authorization, integration with notification/identity, and tests. Enforce PRIVATE/PROTECTED/PUBLIC and Chat restrictions per matrix, keep historical Chat on revoke/PRIVATE and resume old Session after re-share to same verified Customer. Handle unregistered invited emails, self-share, duplicate/concurrent requests, idempotent messages, ordered history and direct nested-resource access. Use existing framework conventions and secrets, and provide verifiable build/test/docker results, including what could not be tested. Ask about blockers before implementing incompatible schema changes.

**END OF SPECIFICATION**
