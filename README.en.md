# eCommerce Lottery Platform

> 中文版：[README.md](README.md)

A spin-wheel lottery backend for eCommerce. The focus is **stock & draw-count control under high concurrency**, on a stateless, horizontally-scalable, environment-configurable deployment. Front/back separated, RESTful API, JWT role grading, with a minimal spin-wheel UI you can play with.

> Three prizes, each with its own stock and win probability, plus a "no prize" (銘謝惠顧) option summing to 100%. Single and multi draw; duplicate-draw prevention, over-draw prevention, per-user draw-limit enforcement.

## Highlights

- **Correctness under concurrency**: the database is the source of truth. Over-draw is made impossible by an atomic conditional update `UPDATE prize SET remaining_stock = remaining_stock - 1 WHERE id = ? AND remaining_stock > 0`. Redis Lua scripts pre-deduct stock and per-user/per-activity quota to keep hot prizes off the DB, but if Redis is down the system stays correct (cold cache falls back to the DB) — only throughput drops.
- **Degrade-to-no-prize**: if a picked prize is sold out, the draw degrades to 銘謝惠顧 instead of retrying.
- **Idempotency**: each draw request carries an idempotency key (Redis `SET NX` + a unique index on `draw_record`), so a replayed request returns the original outcome.
- **Integer probability**: probabilities are stored as basis points (0–10000) and validated to sum to exactly 10000 when an activity goes live — no floating-point drift.

## Architecture

Modular monolith with DDD boundaries (`activity` / `prize` / `draw` / `riskcontrol` / `auth` / `admin`), each split into `domain / application / api`. Not split into microservices — at this scale that only adds distributed-transaction and ops cost — but the boundaries are clean, so a future split (and CQRS read/write separation) is low-cost. See [docs/architecture.md](docs/architecture.md).

Stateless app (JWT, no server-side session) → scales horizontally; shared state lives only in MySQL + Redis. See [docs/scaling.md](docs/scaling.md) for a 2-replica + nginx demo, and `deploy/k8s/deployment.yaml` for the Kubernetes equivalent.

## Tech stack

Java 17 · Spring Boot 3.3 (Web / Data JPA / Security / Validation / Actuator) · MySQL 8 · Redis 7 · Flyway · JWT (jjwt) · springdoc-openapi · JUnit 5 + Mockito + Testcontainers · Docker / Kubernetes.

## Quick start

```bash
docker compose up --build
```

- App / UI: <http://localhost:8080>  ·  Admin console: <http://localhost:8080/admin.html>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Seed accounts: `alice / alice123` (USER), `admin / admin123` (ADMIN)

## Tests

```bash
./mvnw test
```

Pure unit tests (probability distribution, boundaries, error cases) run without any infrastructure. The integration tests (`ApiIntegrationTest` for the HTTP/auth/validation layer, and `ConcurrentDrawTest` proving no over-draw under 300 concurrent draws) use Testcontainers and need Docker.

## API overview

Auth: `POST /api/auth/{register,login}` (login returns token + role). User: `GET /api/activities`, `GET /api/activities/{id}`, `POST /api/activities/{id}/draws` (times 1–10), `GET /api/activities/{id}/{my-records,my-quota}`. Admin (ADMIN role): `GET /api/admin/activities`, `POST /api/admin/activities`, `PUT /api/admin/activities/{id}`, `POST /api/admin/activities/{id}/prizes`, `PUT /api/admin/prizes/{id}`, `DELETE /api/admin/prizes/{id}`, `GET /api/admin/activities/{id}/stats`. Every response is wrapped as `{ success, code, message, data }`. Full parameters in Swagger at `/swagger-ui.html`.
