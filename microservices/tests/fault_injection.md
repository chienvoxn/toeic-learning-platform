# Fault injection procedure

Prerequisite: `docker compose up --build -d`, register a learner, create a FREE attempt with some answers.

## A. Interaction service failure

1. `docker compose stop interaction-service`
2. Submit existing attempt through UI (or smoke script). Expect HTTP 200 and consistent result.
3. `docker compose start interaction-service`
4. Wait 3–15 seconds, then query `GET /api/v1/interactions/activities/me` with the same learner bearer. Expect `ATTEMPT_GRADED`.
5. Capture before/after screenshots and timing; do not claim passed without observing it.

## B. RabbitMQ failure

1. Open a new unfinished attempt.
2. `docker compose stop rabbitmq`
3. Submit; expect grade success (transaction to local PostgreSQL).
4. `docker compose start rabbitmq`
5. Wait for outbox retry; check logs `docker compose logs assessment-service interaction-service` and activity endpoint. Note lack of publisher confirms in this MVP.

## C. Consistency and access control

- Submit same attempt twice; expect same saved result and only one `event_outbox` row for the attempt.
- Create a second user and request the first user's attempt; expect 404.
- Call `POST /ms/v1/contents/quizzes` with learner token; expect 403.
- Try internal service snapshot URL through Gateway; expect 404.
- Try Premium quiz with FREE; expect 403.
