# SettleUp — Full Build Specification
### Group Expense Splitting & Settlement Platform (Splitwise-style, with ledger accounting + settlement simulation)

This document is a complete, unambiguous build spec intended to be handed to an AI coding agent. It defines scope, architecture, database schema, API contracts, event/queue formats, folder structure, and a phased build order. Follow the phases in order — do not skip ahead to Phase 3+ features before Phase 1 is fully working and tested.

---

## 0. Project Identity

- **Project name (placeholder):** SettleUp
- **One-line pitch:** A group expense-splitting app that uses double-entry ledger accounting (not mutable balances), supports offline-first mobile usage, and simulates real settlement/payment flows asynchronously.
- **Repos to create (3 separate folders/repos):**
  1. `settleup-backend` — Spring Boot API
  2. `settleup-android` — Kotlin + Jetpack Compose app
  3. `settleup-web` — React admin/analytics dashboard

---

## 1. Tech Stack (fixed — do not substitute without reason)

| Layer | Choice | Version guidance |
|---|---|---|
| Backend framework | Spring Boot | 3.3.x, Java 21 |
| Build tool | Maven | 3.9.x |
| Database | PostgreSQL | 16.x |
| ORM | Spring Data JPA + Hibernate | bundled with Spring Boot |
| Cache | Redis | 7.x |
| Message broker | RabbitMQ | 3.13.x |
| Auth | Spring Security + JWT (jjwt library) | — |
| Real-time | Spring WebSocket (STOMP over SockJS) | — |
| Android | Kotlin | 2.0.x |
| Android UI | Jetpack Compose (Material 3) | latest stable |
| Android local DB | Room | latest stable |
| Android background sync | WorkManager | latest stable |
| Android networking | Retrofit + OkHttp | latest stable |
| Android DI | Hilt | latest stable |
| Web frontend | React + Vite | React 18.x |
| Web state/data | React Query (TanStack Query) | latest stable |
| Web styling | Tailwind CSS | latest stable |
| Push notifications | Firebase Cloud Messaging (FCM) | — |
| Containerization | Docker + docker-compose (for MySQL, Redis, RabbitMQ locally) | — |

---

## 2. High-Level Architecture

```
Android App (Compose, Room, WorkManager)  ─┐
                                            ├──> REST + WebSocket ──> Spring Boot API
React Web App (Vite, React Query)         ─┘                              │
                                                                            ├──> PostgreSQL (ledger, users, groups)
                                                                            ├──> Redis (cache, rate limit, sessions)
                                                                            └──> RabbitMQ (settlement + notification queues)
                                                                                     │
                                                                            Notification Worker (consumes queue) ──> FCM
```

---

## 3. Core Domain Concept — Double-Entry Ledger (READ THIS BEFORE CODING ANYTHING)

Do **not** store a mutable `balance` column anywhere. All balances are **derived** by summing ledger entries. This is the single most important architectural rule in this project.

**Rule:** Every expense creates one or more paired `LedgerEntry` rows such that for any transaction, `SUM(debit_amount) == SUM(credit_amount)`.

Example: Karan pays ₹1200 for dinner, split equally 4 ways (Karan, Aman, Riya, Sam):
- 1 credit entry: Karan's account, credit ₹1200 (he paid out cash, so he is "owed" this)
- 3 debit entries: Aman ₹300, Riya ₹300, Sam ₹300 (they owe)
- Karan also gets a debit entry of ₹300 for his own share (he consumed ₹300 of the ₹1200), netting his real credit to ₹900.

A user's balance in a group = `SUM(credit_amount) - SUM(debit_amount)` across all their ledger entries in that group. Positive = others owe them. Negative = they owe others.

**Corrections are never edits.** If an expense entry is wrong, insert a reversal (equal and opposite entries referencing the original `transaction_id`), then insert the corrected entries as a new transaction. Ledger rows are immutable once written (no UPDATE, no DELETE — enforce this at the application layer).

---

## 4. Database Schema (PostgreSQL)

Create these tables exactly as specified. Use `BIGSERIAL` for surrogate keys, `UUID` (via the `pgcrypto` or `uuid-ossp` extension, default `gen_random_uuid()`) for public-facing IDs, `NUMERIC(12,2)` for all money fields (never FLOAT/DOUBLE/REAL). Enable the extension once at the top of the first migration:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

Postgres has no `ENUM` column shorthand and no `ON UPDATE CURRENT_TIMESTAMP` clause like MySQL. Enum-like fields below use `VARCHAR` with a `CHECK` constraint, and `updated_at` columns are maintained via a shared trigger function (defined once, reused per table):

```sql
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

### 4.1 `users`
```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  phone VARCHAR(20),
  fcm_token VARCHAR(255),
  is_premium BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

### 4.2 `groups`
```sql
CREATE TABLE groups (
  id BIGSERIAL PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  name VARCHAR(150) NOT NULL,
  description VARCHAR(500),
  created_by BIGINT NOT NULL REFERENCES users(id),
  default_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
  budget_amount NUMERIC(12,2) DEFAULT NULL,
  budget_alert_threshold_pct SMALLINT DEFAULT 80,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### 4.3 `group_members`
```sql
CREATE TABLE group_members (
  id BIGSERIAL PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  role VARCHAR(10) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER','MEMBER')),
  joined_at TIMESTAMP NOT NULL DEFAULT now(),
  UNIQUE (group_id, user_id)
);
```

### 4.4 `expense_transactions`
```sql
-- reversed_transaction_id is set if this txn reverses another (see Section 3)
CREATE TABLE expense_transactions (
  id BIGSERIAL PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  group_id BIGINT NOT NULL REFERENCES groups(id),
  paid_by BIGINT NOT NULL REFERENCES users(id),
  description VARCHAR(255) NOT NULL,
  total_amount NUMERIC(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
  split_type VARCHAR(12) NOT NULL CHECK (split_type IN ('EQUAL','PERCENTAGE','CUSTOM')),
  reversed_transaction_id BIGINT DEFAULT NULL REFERENCES expense_transactions(id),
  created_by BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### 4.5 `ledger_entries` (append-only, never updated/deleted)
```sql
CREATE TABLE ledger_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_id BIGINT NOT NULL,
  group_id BIGINT NOT NULL,
  account_user_id BIGINT NOT NULL,
  entry_type ENUM('DEBIT','CREDIT') NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (transaction_id) REFERENCES expense_transactions(id),
  FOREIGN KEY (group_id) REFERENCES groups(id),
  FOREIGN KEY (account_user_id) REFERENCES users(id),
  INDEX idx_group_user (group_id, account_user_id)
);
```

### 4.6 `settlements`
```sql
CREATE TABLE settlements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  public_id CHAR(36) NOT NULL UNIQUE,
  group_id BIGINT NOT NULL,
  payer_id BIGINT NOT NULL,
  payee_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
  idempotency_key CHAR(36) NOT NULL UNIQUE,
  mock_upi_ref VARCHAR(50),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  FOREIGN KEY (group_id) REFERENCES groups(id),
  FOREIGN KEY (payer_id) REFERENCES users(id),
  FOREIGN KEY (payee_id) REFERENCES users(id)
);
```

### 4.7 `notifications`
```sql
CREATE TABLE notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type ENUM('EXPENSE_ADDED','SETTLEMENT_COMPLETE','BUDGET_ALERT','GROUP_INVITE') NOT NULL,
  payload_json JSON NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);
```

---

## 5. REST API Contract

Base URL: `/api/v1`. All authenticated routes require header `Authorization: Bearer <jwt>`. All responses are JSON. All money fields are strings formatted to 2 decimals (avoid float precision issues in JSON, e.g. `"amount": "300.00"`).

### 5.1 Auth
```
POST /api/v1/auth/register
Body: { "name": "Karan", "email": "karan@example.com", "password": "..." }
Response 201: { "userId": "uuid", "token": "jwt", "refreshToken": "jwt" }

POST /api/v1/auth/login
Body: { "email": "...", "password": "..." }
Response 200: { "userId": "uuid", "token": "jwt", "refreshToken": "jwt" }

POST /api/v1/auth/refresh
Body: { "refreshToken": "jwt" }
Response 200: { "token": "jwt" }
```

### 5.2 Groups
```
POST /api/v1/groups
Body: { "name": "Goa Trip", "description": "...", "currency": "INR", "budgetAmount": 20000 }
Response 201: { "groupId": "uuid", ... }

GET /api/v1/groups                 -> list of groups current user belongs to
GET /api/v1/groups/{groupId}       -> group details + members
POST /api/v1/groups/{groupId}/members
Body: { "email": "friend@example.com" }
Response 200: { "added": true }

GET /api/v1/groups/{groupId}/balances
Response 200: {
  "balances": [
    { "userId": "uuid", "name": "Aman", "netBalance": "-300.00" },
    { "userId": "uuid", "name": "Karan", "netBalance": "900.00" }
  ]
}

GET /api/v1/groups/{groupId}/simplified-debts
Response 200: {
  "settlementsSuggested": [
    { "from": "Aman", "to": "Karan", "amount": "300.00" }
  ]
}
```

### 5.3 Expenses
```
POST /api/v1/groups/{groupId}/expenses
Body: {
  "description": "Dinner",
  "totalAmount": "1200.00",
  "paidBy": "userUuid",
  "splitType": "EQUAL",   // or PERCENTAGE / CUSTOM
  "splits": [                     // required only for PERCENTAGE / CUSTOM
    { "userId": "uuid", "value": "300.00" }
  ]
}
Response 201: { "transactionId": "uuid", "ledgerEntries": [...] }

GET /api/v1/groups/{groupId}/expenses?page=0&size=20
Response 200: { "content": [...], "totalElements": N }

DELETE /api/v1/expenses/{transactionId}
-> Creates a reversal transaction (does NOT hard-delete). Response 200: { "reversalTransactionId": "uuid" }
```

### 5.4 Settlements
```
POST /api/v1/groups/{groupId}/settlements
Body: {
  "payeeId": "uuid",
  "amount": "300.00",
  "idempotencyKey": "client-generated-uuid"
}
Response 202: { "settlementId": "uuid", "status": "PENDING" }
-> This enqueues a message to RabbitMQ; the worker processes it and updates status async.

GET /api/v1/settlements/{settlementId}
Response 200: { "settlementId": "uuid", "status": "COMPLETED", "mockUpiRef": "UPI123456" }
```

### 5.5 Notifications
```
GET /api/v1/notifications?unreadOnly=true
POST /api/v1/notifications/{id}/read
```

### Error format (use for every error response)
```json
{
  "timestamp": "2026-07-28T10:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "totalAmount must be greater than 0",
  "path": "/api/v1/groups/{groupId}/expenses"
}
```

---

## 6. WebSocket Contract (STOMP over SockJS)

- Endpoint: `/ws` (SockJS handshake)
- Client subscribes to: `/topic/group/{groupId}/balances` and `/topic/group/{groupId}/expenses`
- Server pushes on:
  - New expense created → publish updated balances to `/topic/group/{groupId}/balances`
  - Settlement completed → publish to `/topic/group/{groupId}/balances`

Message format pushed to clients:
```json
{
  "eventType": "EXPENSE_ADDED",
  "groupId": "uuid",
  "transactionId": "uuid",
  "updatedBalances": [ { "userId": "uuid", "netBalance": "900.00" } ]
}
```

---

## 7. RabbitMQ Queues

| Queue name | Producer | Consumer | Message |
|---|---|---|---|
| `settlement.process.queue` | API on settlement creation | Settlement Worker | `{ "settlementId": "uuid", "idempotencyKey": "uuid" }` |
| `notification.dispatch.queue` | API/Worker on events | Notification Worker | `{ "userId": "uuid", "type": "EXPENSE_ADDED", "payload": {...} }` |
| `budget.alert.queue` | Expense service when budget threshold crossed | Notification Worker | `{ "groupId": "uuid", "pctUsed": 82 }` |

**Idempotency requirement:** Settlement Worker must check `idempotency_key` uniqueness in the `settlements` table before processing — if a duplicate message arrives (e.g., RabbitMQ redelivery), it must be a no-op, not a double-settlement.

---

## 8. Debt Simplification Algorithm (for `/simplified-debts` endpoint)

1. Compute net balance per user in the group (positive = owed money, negative = owes money).
2. Put creditors (positive balance) in a max-heap, debtors (negative balance) in a max-heap (by absolute value).
3. Repeatedly match the largest debtor with the largest creditor, settle `min(debtorAmount, creditorAmount)`, push remainder back into the appropriate heap, record a suggested settlement `{from, to, amount}`.
4. Repeat until all balances are zero.
5. This minimizes the number of transactions needed to settle the group (classic greedy min-cash-flow approach).

Implement this as a pure function/service class (`DebtSimplificationService`) with unit tests covering: equal splits, uneven splits, a group where one person owes everyone, a group with already-zero balances.

---

## 9. Phased Build Plan (build and test each phase fully before moving on)

### Phase 1 — Core MVP (backend + basic Android + basic web)
1. Set up `settleup-backend`: Spring Boot project, MySQL connection via Docker Compose, Flyway/Liquibase migration for all tables in Section 4.
2. Implement JWT auth (register/login/refresh).
3. Implement Groups CRUD + membership.
4. Implement Expense creation → ledger entry generation (equal split first, then percentage/custom).
5. Implement `/balances` and `/simplified-debts` endpoints.
6. Write unit tests for `DebtSimplificationService` and ledger balance calculation.
7. Android: auth screens, group list, group detail, add expense form, balance view (Compose, Retrofit, Hilt — no Room/offline yet).
8. Web: login, group list/detail, balances table, add expense form (React + React Query + Tailwind).

**Exit criteria:** A user can register, create a group, add members, log expenses with equal/custom splits, and see correct derived balances on both Android and Web.

### Phase 2 — Real-time + Caching
1. Add Redis: cache computed group balances, invalidate cache on new ledger entry write.
2. Add rate limiting middleware on expense creation endpoint (e.g., max 20 expenses/minute/user via Redis counter).
3. Implement WebSocket (STOMP) broadcasting balance updates on expense creation.
4. Android + Web: subscribe to WebSocket topic, update UI live when another member adds an expense.

**Exit criteria:** Opening the app on two devices in the same group, adding an expense on one device shows the updated balance on the other device within ~1 second without a manual refresh.

### Phase 3 — Settlement Simulation
1. Add RabbitMQ; implement `POST /settlements` → publish to `settlement.process.queue`.
2. Implement Settlement Worker (separate consumer class or separate Spring Boot module): consumes queue, checks idempotency key, simulates a mock UPI reference generation with a short artificial delay, updates settlement status to COMPLETED, writes a reversing/settling ledger entry, publishes a WebSocket update.
3. Implement Notification Worker: consumes `notification.dispatch.queue`, sends FCM push.
4. Android + Web: "Settle Up" button flow — shows PENDING → COMPLETED status transition (poll or WebSocket).

**Exit criteria:** Triggering a settlement resolves the debt between two users, updates ledger, and sends a push notification — without blocking the API response (settlement creation returns immediately with PENDING status).

### Phase 4 — Offline-First Android
1. Add Room database mirroring: groups, expenses (pending + synced), balances cache.
2. Add WorkManager job: when device regains connectivity, push any locally-created pending expenses to the backend, then pull latest state.
3. Conflict resolution rule (keep simple, and be ready to justify it in interviews): **last-write-wins on expense metadata edits; expense creation is additive and never conflicts** (each new expense is a new transaction, not an edit) — this sidesteps most conflict complexity by design.
4. UI: show a "pending sync" badge on expenses created offline.

**Exit criteria:** Turn off network on Android device, add 2-3 expenses, turn network back on — expenses sync to backend automatically and appear correctly in the ledger without duplication.

### Phase 5 — Business Layer
1. Group budgets: set `budget_amount` on group creation; after each expense, check total group spend against budget; if it crosses `budget_alert_threshold_pct`, publish to `budget.alert.queue`.
2. Premium tier simulation: free tier capped at 3 active groups (enforce in `POST /groups`); premium users (`is_premium = true`) bypass the cap and unlock CSV/PDF export endpoint (`GET /api/v1/groups/{groupId}/export?format=csv`).
3. Web dashboard: add an admin/analytics view — total transaction volume, most active groups, settlement success rate (this becomes your "impressive screenshot" for the resume/demo).

**Exit criteria:** A demo-ready app with budgets, a premium gate, and an analytics dashboard screenshot worth showing in an interview.

---

## 10. Folder Structure

### `settleup-backend/`
```
src/main/java/com/settleup/
  config/          (SecurityConfig, WebSocketConfig, RabbitMQConfig, RedisConfig)
  controller/       (AuthController, GroupController, ExpenseController, SettlementController)
  service/          (AuthService, GroupService, ExpenseService, LedgerService,
                     DebtSimplificationService, SettlementService, NotificationService)
  repository/       (JPA repositories per entity)
  entity/           (User, Group, GroupMember, ExpenseTransaction, LedgerEntry, Settlement, Notification)
  dto/              (request/response DTOs per endpoint)
  worker/           (SettlementWorker, NotificationWorker — RabbitListener classes)
  exception/        (GlobalExceptionHandler, custom exceptions)
  security/         (JwtProvider, JwtAuthFilter)
src/main/resources/
  application.yml
  db/migration/     (Flyway SQL scripts, V1__init.sql etc.)
docker-compose.yml  (mysql, redis, rabbitmq services)
```

### `settleup-android/`
```
app/src/main/java/com/settleup/android/
  data/
    local/          (Room entities, DAOs, database class)
    remote/         (Retrofit API interfaces, DTOs)
    repository/     (Repository classes bridging local+remote)
  di/               (Hilt modules)
  domain/           (use-case classes, e.g., AddExpenseUseCase)
  ui/
    auth/           (LoginScreen, RegisterScreen — Composables)
    groups/         (GroupListScreen, GroupDetailScreen)
    expenses/       (AddExpenseScreen)
    settlements/    (SettleUpScreen)
    common/         (shared Composables, theme)
  worker/           (SyncWorker — WorkManager)
  MainActivity.kt
```

### `settleup-web/`
```
src/
  api/              (axios/fetch client, endpoint functions)
  components/       (shared UI components)
  pages/            (Login, GroupList, GroupDetail, Analytics)
  hooks/            (React Query hooks per resource)
  store/            (auth context/state)
  App.tsx
```

---

## 11. Non-Functional Requirements (mention these explicitly to the agent so it doesn't skip them)

- All money arithmetic in the backend must use `BigDecimal`, never `double`/`float`.
- All list endpoints must be paginated (`page`, `size` query params, default size 20).
- Passwords hashed with BCrypt, never stored plain.
- JWT access token expiry: 15 minutes; refresh token expiry: 7 days.
- All timestamps stored in UTC.
- Ledger entries are immutable: no repository method should expose `update` or `delete` for `LedgerEntry`.
- Add basic integration tests (Testcontainers with MySQL) for the expense creation → ledger entry flow, since this is the core correctness guarantee of the whole app.
- Add a `README.md` per repo with setup instructions (docker-compose up, env vars needed, how to run migrations, how to run tests).

---

## 12. What to Tell the Interviewer (once built)

- "I modeled expenses as double-entry ledger transactions instead of mutable balances, so the system has an immutable audit trail and balances are always derivable and correct even under concurrent writes."
- "Settlement processing is decoupled via RabbitMQ so the API isn't blocked by simulated payment gateway latency, and I used idempotency keys to guard against duplicate processing from message redelivery."
- "Balance reads are cached in Redis and invalidated on every new ledger write, since balance lookups are far more frequent than expense writes."
- "The Android app is offline-first — expenses created offline are queued locally in Room and synced via WorkManager, and I avoided most conflict-resolution complexity by making expense creation additive rather than editable."
- "Debt settlement suggestions use a greedy min-cash-flow algorithm to minimize the number of transactions needed to settle a group."

---

## 13. Build Order Summary (for the agent to follow literally)

1. Backend: DB schema + migrations
2. Backend: Auth
3. Backend: Groups + Members
4. Backend: Expenses + Ledger (with unit tests)
5. Backend: Balances + Debt Simplification (with unit tests)
6. Android: Auth + Group + Expense screens (online-only)
7. Web: Auth + Group + Expense screens
8. Backend: Redis caching + rate limiting
9. Backend: WebSocket broadcasting
10. Android/Web: WebSocket live updates
11. Backend: RabbitMQ + Settlement Worker + Notification Worker
12. Android/Web: Settlement flow UI
13. Android: Room + WorkManager offline sync
14. Backend: Budgets + Premium gating + Export
15. Web: Analytics dashboard

Do not reorder these steps. Each step should be fully working and manually verified before starting the next.
