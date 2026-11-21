# Mapping from original TOEIC modular monolith to the standalone microservices MVP

**Source review:** `chienvoxn/toeic-learning-platform`, branch `develop` (GitHub tree and selected code/files inspected on 21 November 2026). The current bundle is an **independent vertical slice** and is NOT a direct copy of these original classes.

| Original code/domain | Proposed context | Implemented in this MVP | Still needed for full migration |
|---|---|---|---|
| `backend/.../identity` including `AuthService`, `BearerSessionFilter`, `RbacService` | Identity | New JWT auth, BCrypt, RBAC tables, admin/teacher role grant | Existing user migration, Google OIDC, password recovery, revocation and complete permission model |
| `backend/.../content` (learning content/versioning/bookmarks/media) | Content | Basic resources and seeded TOEIC quizzes/teacher authoring | Published content versions, bookmark history, media reuse, MinIO parity |
| `backend/.../questionbank` (`PracticeQuestionCatalog`, question versions, authoring) | Content/Question Bank | New quiz+question tables inside Content | Immutable version graph, group versions, media and authoring concurrency features |
| `backend/.../assessment` (`PracticeSessionService`, `PracticeSubmissionService`, grading snapshots) | Assessment | New snapshot-at-start, answer save, grading, locks, local outbox | Existing Part/Section selection, exact source migration, scoring variants, timer and full suite |
| `backend/.../vocabulary` (`SrsScheduler`, review states/history) | Vocabulary later | Not in this MVP | Entire SRS/vocabulary domain migration |
| `backend/.../listening` (S3-compatible media) | Content/Media later | Not in this MVP | Streaming, presigned storage, transcripts, dictation/shadowing |
| `backend/.../ai` + `AI/` FastAPI RAG | AI later | Not in this MVP | Existing RAG tutor integration with authorization |
| `backend/.../notification` skeleton | Notification later | Not in this MVP | Notification consumer and push/email |
| `backend/.../analytics` skeleton | Analytics later | Activity view in Interaction only | Learner analytics and business reports |
| No full `membership` bounded context | Membership | FREE/PRO entitlement, per-user quota, DB | Subscription lifecycle, billing/provider adapter, premium entitlements with approval |
| No full `interaction` bounded context | Interaction | Reviews and event-derived activity | Comments/reactions/moderation and advanced analytics |

## Why not simply move the original packages?

Original code uses a single DB with relational joins, Spring Security principal and direct Java calls. In particular, `PracticeSessionService` depends on `PracticeQuestionCatalog`; `PracticeSubmissionService` assumes its pinned snapshots and grading remain in one local transaction; `BearerSessionFilter` uses an opaque token and Identity DB access. Copying these classes without decoupling persistence/contracts produces distributed calls that will not compile or preserve the original behavior.

## Progressive full migration plan

1. **Baseline** `develop` tag, preserve original Flyway V1–V35, create a new feature branch, run all original tests.
2. **Identity** migration with dual-read / account migration, JWT/JWKS transition, and guaranteed no privilege escalation; update frontend login contract.
3. **Content & Question Bank** snapshot APIs and media contracts; migrate data with deterministic UUID mapping and checksums, freeze authoring writes during cutover.
4. **Assessment** port exact current scoring, Section/Test rules, backfill historical pinned snapshots; isolate DB transaction; author separate contract and concurrency tests.
5. **Interaction/Membership** link existing user IDs, implement validated entitlements and event-based projections.
6. **Vocabulary, AI and listening** gradually route endpoints through Gateway while retaining legacy until parity tests pass.
7. **Strangler Fig cutover**: per-route gateway switch, consumer compatibility, test and rollback plan.

**Do not present this standalone bundle as “100% original feature migration.”** It is suitable for a focused KTHDV/KTPM microservices architecture demo once built and tested locally.
