CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE account (
                         id                      BIGSERIAL PRIMARY KEY,
                         client_id               UUID NOT NULL REFERENCES clients(id),
                         account_type            VARCHAR(20) NOT NULL CHECK (account_type IN ('DEBIT', 'CREDIT', 'JOB')),
                         account_number          VARCHAR(34) NOT NULL UNIQUE,
                         balance                 NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
                         phone                   VARCHAR(100),
                         currency                VARCHAR(3) NOT NULL DEFAULT 'USD',
                         status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
                         opened_at               TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                         closed_at               TIMESTAMP WITH TIME ZONE,

    -- DEBIT specific
                         daily_withdrawal_limit  NUMERIC(19, 4),
                         overdraft_limit         NUMERIC(19, 4),

    -- CREDIT specific
                         credit_limit            NUMERIC(19, 4),
                         apr                     NUMERIC(5, 2),
                         grace_period_days       INTEGER,
                         statement_day           INTEGER CHECK (statement_day BETWEEN 1 AND 31),

    -- JOB / SALARY specific
                         employer_name           VARCHAR(255),
                         employer_id             VARCHAR(100),
                         salary_day              INTEGER CHECK (salary_day BETWEEN 1 AND 31)
);

CREATE INDEX idx_account_client_type ON account(client_id, account_type);
CREATE INDEX idx_account_status ON account(status);