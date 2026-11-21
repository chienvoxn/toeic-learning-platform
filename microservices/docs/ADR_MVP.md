# Architecture Decision Records — TOEIC Microservices MVP

## ADR-001 — Five business services vs one modular monolith
**Status:** Accepted for course MVP. **Context:** KTHDV requires at least four core microservices and Gateway. Monolith is more operationally simple but does not demonstrate independent service/database ownership. **Decision:** Identity, Content, Assessment, Interaction and Membership deployed independently with five databases. **Consequence:** More operations complexity, no FK across services, network failure handling. **Alternative rejected:** rename package without independent processes.

## ADR-002 — JWT access tokens for Identity
**Status:** Accepted for demo, production hardening pending. **Context:** original platform uses opaque bearer sessions stored in DB; KTHDV Hướng 3 requires IAM issuing JWT with roles/permissions. **Decision:** 30-minute HS256 signed tokens with role, permissions, subject, issuer, audience and expiration. Verify at service; do not accept `userId` from body. **Tradeoff:** revocation delayed, secret shared by services. **Recommended production:** RS256/EdDSA with JWKS, refresh/rotation/revocation.

## ADR-003 — RabbitMQ + transactional outbox
**Status:** Accepted with publisher-confirm limitation. **Context:** success of grading should not depend on Interaction uptime. **Decision:** save grade and outbox in single Assessment DB transaction, publish `AttemptGraded` on background poll to RabbitMQ, consume in Interaction with event ID as PK. **Alternative:** synchronous HTTP write to Interaction (rejected: failure coupling). **Tradeoff:** eventual consistency, retry/duplicate handling, confirm gap. In production wait for publisher confirms.

## ADR-004 — PostgreSQL per service
**Status:** Accepted. **Context:** domain separation and transactional correctness. **Decision:** separate PostgreSQL databases; each service owns migrations and tables; cross-service reference userId, quizId are UUID without FK across DB. **Alternative:** shared DB schema (rejected because it hides coupling). **Tradeoff:** reference integrity verified through API and business rules.

## ADR-005 — Small standalone web UI
**Status:** Accepted for exam demo. **Context:** existing complete React and React Native UIs depend on monolith endpoints; migrating all features is substantially larger. **Decision:** a small same-origin HTML/CSS/JS frontend served by Nginx tests the new 5-service workflow without copying old UI. **Consequence:** works for grading demonstration; does not prove full compatibility with existing frontend. Later migration via Strangler Fig routing and frontend contract adapters.
