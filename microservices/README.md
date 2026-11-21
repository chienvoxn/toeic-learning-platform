# TOEIC Learning Platform — Microservices Demo MVP

> **Scope:** This is an **independent demonstrable MVP**, derived from a review of `chienvoxn/toeic-learning-platform@develop` and the earlier migration kits. It is **NOT** a byte-for-byte migration of the existing full TOEIC platform. It preserves the *domain concepts* of identity, content, assessment, interactions and entitlement, and provides a new integrated web UI. Full original features (Google OIDC, SRS, mobile, original RAG tutor, teacher versioning, historical grading migrations) remain in the **original repository**, not inside this bundle.

## 1. Requirements / tools

- Docker Engine with Docker Compose v2 (recommended 8 GB free RAM)
- A browser; Java/Maven are not required on the host because each service builds in Docker
- For local demo only; never deploy with the example secrets or demo plan escalation

## 2. Start the whole system

From this `microservices` directory:

```bash
cp .env.example .env
# Replace DB_PASSWORD, JWT_SECRET, INTERNAL_SERVICE_KEY and RABBIT_PASSWORD
# Set DEMO_TEACHER_EMAIL and DEMO_TEACHER_PASSWORD if authoring a quiz from the teacher account

docker compose config
docker compose up --build -d
docker compose ps
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
# Edit .env and change secrets

docker compose config
docker compose up --build -d
docker compose ps
```

- Web UI & Gateway: **http://localhost:8088**
- RabbitMQ dashboard: **http://localhost:15672** (use RABBIT_USER/RABBIT_PASSWORD)
- Only the Gateway and RabbitMQ admin are published; databases and app ports stay inside Docker network.
- First run initializes five PostgreSQL volumes and applies Flyway migrations with 2 small demonstration quizzes.

> If `docker compose config` fails due to unsupported anchors, use modern Docker Compose v2. If containers fail, inspect `docker compose logs --tail=150 identity-service content-service assessment-service interaction-service membership-service`. Do **not** use `down -v` unless deliberately discarding demo data.

## 3. Demo flow (7–10 minutes)

1. Register a learner and sign in. `Identity Service` issues a signed HS256 JWT with role `LEARNER` and permissions.
2. View available quizzes in `Content Service` — Part 5 (FREE) and Part 6 (PRO). Internal content snapshots containing answer keys cannot be reached via public Gateway routes.
3. Start the FREE quiz. `Assessment Service` calls Content's **internal snapshot API**, creates its own immutable question snapshot, and checks Membership's entitlement API. Choose A/B/C/D and submit; submit is idempotent and serializes concurrent submissions with a pessimistic row lock.
4. Attempt to start PRO quiz while FREE: forbidden; go to **Membership**, choose PRO **in demo mode** (no payment), retry.
5. Add a review in **Interaction**; open **Activities** after submitting a quiz to see the asynchronously delivered `AttemptGraded` event.
6. Demonstrate fault isolation: `docker compose stop interaction-service`, submit an ungraded attempt; Assessment should still grade it. Restart Interaction and check Activities. For an extended test stop `rabbitmq`, submit (outbox persisted), restart RabbitMQ and check eventual delivery.
7. Explain PostgreSQL isolation: each service connects to its **own database**, with UUID references rather than cross-database FKs.

Optional teacher account: set `DEMO_TEACHER_EMAIL` and `DEMO_TEACHER_PASSWORD` in `.env` **before first starting Identity**. A demo admin can also be provisioned with `DEMO_ADMIN_EMAIL` / `DEMO_ADMIN_PASSWORD`, then use `POST /ms/v1/identity/admin/users/{id}/roles` with `{"role":"TEACHER"}` to grant roles. `GET /ms/v1/identity/rbac/me` shows the caller's resolved DB permissions. The teacher can create new quizzes via `POST /ms/v1/contents/quizzes` with the teacher JWT; learner cannot.

## 4. Services

| Service | Port internal | DB | Focus |
|---|---|---|---|
| identity-service | 8084 | identity_db | registration, BCrypt, JWT, RBAC |
| content-service | 8085 | content_db | learning resources, published TOEIC quizzes, authoring permission |
| assessment-service | 8086 | assessment_db | attempt snapshot, choices, grading, outbox |
| interaction-service | 8083 | interaction_db | reviews, activity event projection |
| membership-service | 8082 | membership_db | FREE/PRO entitlement and daily limit |
| gateway | 8088 | none | single public API + static UI |
| rabbitmq | 5672 | broker state | durable interaction event queue |

**Design:** database-per-service; no direct SQL access across service boundaries. All external requests use Gateway. Internal HTTP endpoints use a shared service key and are blocked by Gateway. Identity and application services verify HMAC JWT signatures with identical `JWT_SECRET` (demo-only tradeoff: prefer asymmetric JWKS in production).

## 5. Key API endpoints

| HTTP | Route | Authentication |
|---|---|---|
| POST | /ms/v1/identity/register | public |
| POST | /ms/v1/identity/login | public |
| GET | /ms/v1/identity/me | JWT |
| GET | /ms/v1/contents/quizzes | public |
| GET | /ms/v1/contents/quizzes/{id} | public, **no answer keys** |
| POST | /ms/v1/contents/quizzes | teacher/admin JWT |
| POST | /ms/v1/attempts | learner JWT |
| GET | /ms/v1/attempts | learner JWT |
| PUT | /ms/v1/attempts/{id}/answers/{questionId} | owner JWT |
| POST | /ms/v1/attempts/{id}/submit | owner JWT |
| GET | /api/v1/memberships/me | JWT |
| PUT | /api/v1/memberships/me | JWT, demo flag only |
| GET | /api/v1/interactions/activities/me | JWT |
| PUT | /api/v1/interactions/contents/{id}/reviews/me | JWT |

For end-to-end smoke test after starting the stack:

```bash
python tests/smoke.py
```

This script uses **only standard Python library**, creates a random learner, checks a free quiz, answer, submit idempotence, Premium restriction, demo PRO entitlement and event delivery.

## 6. Deliverables for KTHDV and KTPM

- `docs/EXAM_ARCHITECTURE.md` — ADD, arc42, C4 L1/L2/L3, runtime, deployment, architecture risks, RBAC model, data ownership
- `docs/ADR_MVP.md` — design decisions, alternatives, consequences
- `docs/REPORT_TEMPLATE.md` — ready-to-fill experiment tables; **no fabricated measurements**
- `tests/smoke.py`, `tests/load.js` — scripts for functional and performance checks

## 7. Explicit boundaries and remaining work

- The scope is a **new exam-ready slice** not migration of the full existing app. Existing Web/Mobile UI, OAuth, vocabulary SRS, audio/media, AI RAG, question versioning, and full-exam workflows have not been ported.
- HS256 shared secret is for a single-machine teaching demo. Token revocation is not immediate; logout in UI only drops browser token. Do not use for production authentication.
- The `PUT /memberships/me` route allows a learner to self-enroll in PRO only while `ALLOW_DEMO_PLAN_CHANGE=true`. This is **not payment**; disable for production. Disabling this flag makes the full premium-flow demo require admin provisioning.
- Outbox transitions to `published=true` when RabbitTemplate returns; the demo does **not** wait for publisher confirms. Production needs broker publisher confirms, dead-letter queue, retries/backoff, idempotency verification under repeated delivery, tracing and alerting.
- Assessment snapshots are immutable at attempt start but authoring only supports new quizzes; the full version/publish/archive workflow from the original repository remains separate.
- An integration test and full Docker-based Maven build are **not claimed to have passed** in the environment in which this artifact was authored, because Docker and dependency downloads were unavailable. See `docs/REPORT_TEMPLATE.md` for what to capture on your machine.

## 8. Existing source integration

The reviewed source is `https://github.com/chienvoxn/toeic-learning-platform/tree/develop` (snapshot at the time of review). This bundle should be kept on a separate feature branch or alongside your repo as `microservices/`; it does not modify Flyway migrations V1–V35 of the original monolith and must not point the new apps at the original `toeic_db`.
