CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- CLIENTS (UUID primary key)
-- ============================================================
CREATE TABLE clients (
                         id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                         first_name      VARCHAR(100) NOT NULL,
                         last_name       VARCHAR(100) NOT NULL,
                         email           VARCHAR(255) NOT NULL UNIQUE,
                         phone           VARCHAR(20),
                         date_of_birth   DATE,
                         created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);