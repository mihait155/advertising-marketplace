CREATE TABLE users
(
    id         UUID PRIMARY KEY,
    full_name  VARCHAR(150) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    phone      VARCHAR(32),
    user_type  VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_user_type CHECK (user_type IN ('MARKETER', 'CUSTOMER', 'ADMIN'))
);

CREATE INDEX idx_users_user_type ON users (user_type);
