# settleup-backend

Spring Boot 3.3.x · Java 21 · Maven · PostgreSQL 16 · Redis 7 · RabbitMQ 3.13

Group expense splitting with **double-entry ledger accounting** — balances are never stored, always derived.

---

## Prerequisites

| Tool | Version |
|---|---|
| Docker Desktop | ≥ 4.x |
| Java (JDK) | 21 |
| Maven | 3.9.x (or use the wrapper `./mvnw`) |

---

## Quick Start

### 1. Clone & enter the directory
```bash
git clone <your-fork>
cd settleup-backend
```

### 2. Start infrastructure (PostgreSQL, Redis, RabbitMQ)
```bash
docker-compose up -d
```

Wait ~15 seconds for all three containers to be healthy. You can verify:
```bash
docker-compose ps
```
All three services should show `healthy`.

- **PostgreSQL** → `localhost:5432` (db: `settleup`, user: `settleup`, pass: `settleup_secret`)
- **Redis** → `localhost:6379` (pass: `redis_secret`)
- **RabbitMQ Management UI** → http://localhost:15672 (user: `settleup`, pass: `rabbitmq_secret`)

### 3. Set required environment variables

The **only** required environment variable is:

```bash
# Linux / macOS
export JWT_SECRET="a-very-long-and-random-secret-at-least-32-chars-for-HS256"

# Windows PowerShell
$env:JWT_SECRET = "a-very-long-and-random-secret-at-least-32-chars-for-HS256"
```

> ⚠️ `JWT_SECRET` must be **at least 32 characters** (256 bits) for HMAC-SHA256.
> Never commit a real secret to source control.

All other settings default to the Docker Compose values and can be overridden:

| Env Var | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `settleup` |
| `DB_USER` | `settleup` |
| `DB_PASSWORD` | `settleup_secret` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | `redis_secret` |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` | `settleup` |
| `RABBITMQ_PASSWORD` | `rabbitmq_secret` |

### 4. Run Flyway migrations & start the server

Flyway migrations run **automatically** on application startup:

```bash
./mvnw spring-boot:run
```

Or build and run the JAR:
```bash
./mvnw package -DskipTests
java -jar target/settleup-backend-0.0.1-SNAPSHOT.jar
```

The API is available at **http://localhost:8080**.

### 5. Verify startup

You should see log lines like:
```
Flyway: Successfully applied 1 migration to schema "public"
Started SettleUpApplication in X.XXX seconds
```

Check health:
```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

---

## Running Tests

### Unit tests (no Docker required)
```bash
./mvnw test
```

### Integration tests (requires Docker — uses Testcontainers)
```bash
./mvnw verify -P integration-tests
```
> Integration tests spin up ephemeral PostgreSQL containers via Testcontainers. Docker must be running.

---

## Database Schema

All schema changes are managed by **Flyway** migrations in:
```
src/main/resources/db/migration/
  V1__init.sql   — initial schema (all 7 tables)
```

To inspect the live schema:
```bash
docker exec -it settleup-postgres psql -U settleup -d settleup -c "\dt"
```

### Key schema rules
- **`ledger_entries`** is **append-only** — no UPDATE, no DELETE, ever
- All money columns are `NUMERIC(12,2)` — never `FLOAT` or `DOUBLE`
- All `updated_at` columns are maintained by the `set_updated_at()` PostgreSQL trigger function
- Public-facing IDs are UUIDs (`gen_random_uuid()` via `pgcrypto` extension)

---

## Project Structure

```
src/main/java/com/settleup/
  config/         SecurityConfig, WebSocketConfig, RabbitMQConfig, RedisConfig
  controller/     AuthController, GroupController, ExpenseController, SettlementController
  service/        AuthService, GroupService, ExpenseService, LedgerService,
                  DebtSimplificationService, SettlementService, NotificationService
  repository/     JPA repositories per entity
  entity/         User, Group, GroupMember, ExpenseTransaction, LedgerEntry, Settlement, Notification
  dto/            Request/response DTOs per endpoint
  worker/         SettlementWorker, NotificationWorker (RabbitListener classes)
  exception/      GlobalExceptionHandler, custom exceptions
  security/       JwtProvider, JwtAuthFilter, UserDetailsServiceImpl
src/main/resources/
  application.yml
  db/migration/V1__init.sql
docker-compose.yml
```

---

## Build Phase Status

| Phase | Step | Status |
|---|---|---|
| Phase 1 | Step 1: Backend foundation (this repo setup) | ✅ Done |
| Phase 1 | Step 2: JWT Auth | 🔲 Next |
| Phase 1 | Step 3: Groups CRUD + membership | 🔲 Pending |
| Phase 1 | Step 4: Expenses + Ledger | 🔲 Pending |
| Phase 1 | Step 5: Balances + Debt Simplification | 🔲 Pending |
| Phase 2 | Redis caching + rate limiting | 🔲 Pending |
| Phase 2 | WebSocket real-time | 🔲 Pending |
| Phase 3 | RabbitMQ settlement worker | 🔲 Pending |

---

## Architecture Decision Notes

### Why double-entry ledger?
Storing mutable balances is a concurrency hazard (last-writer-wins on concurrent expense additions corrupts balances). The ledger append-only model is naturally concurrent — each expense just inserts rows, and balances are always derived by `SUM(credit) - SUM(debit)`. This also gives a complete audit trail.

### Why BigDecimal everywhere?
IEEE 754 floats cannot represent `0.1` exactly. `0.1 + 0.2 ≠ 0.3` in floating point. For a financial app, this is unacceptable. All money arithmetic uses `BigDecimal` with explicit scale (`HALF_UP` rounding).

### Why NUMERIC(12,2) in the DB?
`NUMERIC` (aka `DECIMAL`) is exact precision. `REAL` and `DOUBLE PRECISION` are not. PostgreSQL maps `NUMERIC(12,2)` directly to Java `BigDecimal`.
