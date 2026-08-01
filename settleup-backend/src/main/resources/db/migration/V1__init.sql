-- =============================================================
-- V1__init.sql  —  SettleUp initial schema
-- PostgreSQL 16
-- ALL MySQL-isms corrected:
--   AUTO_INCREMENT → BIGSERIAL
--   ENUM(...)      → VARCHAR + CHECK constraint
--   DECIMAL        → NUMERIC
--   CURRENT_TIMESTAMP → now()
--   CHAR(36) UUIDs → UUID
--   INDEX ...      → standalone CREATE INDEX
--   JSON           → JSONB
--   TIMESTAMP NULL → TIMESTAMP (nullable by default in PG)
-- =============================================================

-- ── Extensions ───────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── Shared trigger function for updated_at ───────────────────
-- Created once, reused by all tables that have an updated_at column.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================
-- 1. users
-- =============================================================
CREATE TABLE users (
    id            BIGSERIAL     PRIMARY KEY,
    public_id     UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(150)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    phone         VARCHAR(20),
    fcm_token     VARCHAR(255),
    is_premium    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================
-- 2. groups
-- Note: "groups" is a reserved keyword in SQL — quoted where necessary
-- by the application layer; table name is kept per spec.
-- =============================================================
CREATE TABLE groups (
    id                          BIGSERIAL       PRIMARY KEY,
    public_id                   UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                        VARCHAR(150)    NOT NULL,
    description                 VARCHAR(500),
    created_by                  BIGINT          NOT NULL REFERENCES users(id),
    default_currency            VARCHAR(3)      NOT NULL DEFAULT 'INR',
    budget_amount               NUMERIC(12,2)   DEFAULT NULL,
    budget_alert_threshold_pct  SMALLINT        DEFAULT 80,
    created_at                  TIMESTAMP       NOT NULL DEFAULT now()
);

-- =============================================================
-- 3. group_members
-- =============================================================
CREATE TABLE group_members (
    id         BIGSERIAL    PRIMARY KEY,
    group_id   BIGINT       NOT NULL REFERENCES groups(id),
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    role       VARCHAR(10)  NOT NULL DEFAULT 'MEMBER'
                            CHECK (role IN ('OWNER', 'MEMBER')),
    joined_at  TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (group_id, user_id)
);

-- =============================================================
-- 4. expense_transactions
-- reversed_transaction_id is set when this txn reverses another
-- (see double-entry reversal rule in spec §3).
-- =============================================================
CREATE TABLE expense_transactions (
    id                       BIGSERIAL     PRIMARY KEY,
    public_id                UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    group_id                 BIGINT        NOT NULL REFERENCES groups(id),
    paid_by                  BIGINT        NOT NULL REFERENCES users(id),
    description              VARCHAR(255)  NOT NULL,
    total_amount             NUMERIC(12,2) NOT NULL,
    currency                 VARCHAR(3)    NOT NULL DEFAULT 'INR',
    split_type               VARCHAR(12)   NOT NULL
                             CHECK (split_type IN ('EQUAL', 'PERCENTAGE', 'CUSTOM')),
    reversed_transaction_id  BIGINT        DEFAULT NULL
                             REFERENCES expense_transactions(id),
    created_by               BIGINT        NOT NULL REFERENCES users(id),
    created_at               TIMESTAMP     NOT NULL DEFAULT now()
);

-- =============================================================
-- 5. ledger_entries
-- APPEND-ONLY — no UPDATE, no DELETE, ever.
-- This is the core immutable audit ledger. Balances are ALWAYS
-- derived by SUM(credit_amount) - SUM(debit_amount) per user/group.
-- =============================================================
CREATE TABLE ledger_entries (
    id               BIGSERIAL     PRIMARY KEY,
    transaction_id   BIGINT        NOT NULL REFERENCES expense_transactions(id),
    group_id         BIGINT        NOT NULL REFERENCES groups(id),
    account_user_id  BIGINT        NOT NULL REFERENCES users(id),
    entry_type       VARCHAR(6)    NOT NULL
                     CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount           NUMERIC(12,2) NOT NULL
                     CHECK (amount > 0),   -- amount is always positive; sign is captured by entry_type
    created_at       TIMESTAMP     NOT NULL DEFAULT now()
);

-- Index for the primary query pattern: balance calculation per (group, user)
CREATE INDEX idx_ledger_group_user
    ON ledger_entries (group_id, account_user_id);

-- Index for looking up all entries for a transaction (e.g., reversal validation)
CREATE INDEX idx_ledger_transaction
    ON ledger_entries (transaction_id);

-- =============================================================
-- 6. settlements
-- =============================================================
CREATE TABLE settlements (
    id               BIGSERIAL     PRIMARY KEY,
    public_id        UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    group_id         BIGINT        NOT NULL REFERENCES groups(id),
    payer_id         BIGINT        NOT NULL REFERENCES users(id),
    payee_id         BIGINT        NOT NULL REFERENCES users(id),
    amount           NUMERIC(12,2) NOT NULL
                     CHECK (amount > 0),
    status           VARCHAR(12)   NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    idempotency_key  UUID          NOT NULL UNIQUE,  -- client-generated, guards against double-processing
    mock_upi_ref     VARCHAR(50),
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    completed_at     TIMESTAMP     -- nullable; set when status → COMPLETED or FAILED
);

-- =============================================================
-- 7. notifications
-- =============================================================
CREATE TABLE notifications (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    type         VARCHAR(25)  NOT NULL
                 CHECK (type IN ('EXPENSE_ADDED', 'SETTLEMENT_COMPLETE',
                                 'BUDGET_ALERT', 'GROUP_INVITE')),
    payload_json JSONB        NOT NULL,   -- JSONB preferred over JSON for indexability
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

-- Index for the most common notification query: unread notifications per user
CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id, is_read);

-- =============================================================
-- End of V1__init.sql
-- =============================================================
