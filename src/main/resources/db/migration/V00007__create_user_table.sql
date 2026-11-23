CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
                       id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       first_name            VARCHAR(100) NOT NULL UNIQUE,
                       last_name             VARCHAR(100) NOT NULL,
                       status                VARCHAR(255) NOT NULL,
                       failed_login_attempts NUMERIC NOT NULL DEFAULT 0,
                       locked_until          DATE,
                       client_id             UUID REFERENCES clients(id), -- Replace 'clients(id)' with your target table
                       created_at            TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                       last_login_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

INSERT INTO users (
    first_name,
    last_name,
    status,
    failed_login_attempts,
    locked_until,
    client_id
)
VALUES (
           'Jane',
           'Doe',
           'active',
           0,
           NULL,
           '827e9353-3fcf-4734-935d-902c6a706987'
       );
