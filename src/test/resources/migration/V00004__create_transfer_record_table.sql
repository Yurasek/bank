CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- CLIENTS (UUID primary key)
-- ============================================================
CREATE TABLE public.transfer_history (
    -- Primary key
                                  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Account references (not foreign keys for performance/flexibility)
                                  from_account        VARCHAR(20) NOT NULL,
                                  to_account          VARCHAR(20) NOT NULL,

    -- Transfer amount
                                  amount              NUMERIC(19, 4) NOT NULL,

    -- Balance snapshot for audit (immutable after creation)
                                  from_balance_before NUMERIC(19, 4) NOT NULL,
                                  to_balance_before   NUMERIC(19, 4) NOT NULL,

    -- Status tracking
                                  status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                  failure_reason      VARCHAR(500),
    -- Timestamps
                                  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);