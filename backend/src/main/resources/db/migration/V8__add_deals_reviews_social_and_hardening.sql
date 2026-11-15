CREATE TABLE login_attempts
(
    attempt_key  VARCHAR(400) PRIMARY KEY,
    failures     INT         NOT NULL,
    window_start TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_login_attempts_window_start ON login_attempts (window_start);

CREATE INDEX idx_images_owner_created_at ON images (owner_id, created_at);

CREATE TABLE deals
(
    id               UUID PRIMARY KEY,
    offer_id         UUID REFERENCES offers (id) ON DELETE SET NULL,
    posting_id       UUID REFERENCES postings (id) ON DELETE SET NULL,
    posting_title    VARCHAR(200)   NOT NULL,
    marketer_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    customer_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    status           VARCHAR(15)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    delivered_at     TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    auto_complete_at TIMESTAMPTZ,
    cancelled_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    cancel_reason    VARCHAR(1000),
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_deals_offer UNIQUE (offer_id),
    CONSTRAINT ck_deals_status CHECK (status IN ('IN_PROGRESS', 'DELIVERED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_deals_amount CHECK (amount >= 0)
);

CREATE INDEX idx_deals_marketer ON deals (marketer_id, created_at DESC);
CREATE INDEX idx_deals_customer ON deals (customer_id, created_at DESC);
CREATE INDEX idx_deals_auto_complete ON deals (auto_complete_at) WHERE status = 'DELIVERED';

CREATE TABLE deliveries
(
    id           UUID PRIMARY KEY,
    deal_id      UUID        NOT NULL REFERENCES deals (id) ON DELETE CASCADE,
    links        TEXT        NOT NULL,
    note         TEXT,
    status       VARCHAR(20) NOT NULL,
    feedback     TEXT,
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_at  TIMESTAMPTZ,
    CONSTRAINT ck_deliveries_status CHECK (status IN ('PENDING', 'APPROVED', 'CHANGES_REQUESTED'))
);

CREATE INDEX idx_deliveries_deal ON deliveries (deal_id, submitted_at);

CREATE TABLE reviews
(
    id          UUID PRIMARY KEY,
    deal_id     UUID        NOT NULL REFERENCES deals (id) ON DELETE CASCADE,
    reviewer_id UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewee_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating      SMALLINT    NOT NULL,
    body        TEXT,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_reviews_deal_reviewee UNIQUE (deal_id, reviewee_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_reviews_reviewee ON reviews (reviewee_id, created_at DESC);

CREATE TABLE social_accounts
(
    id                      UUID PRIMARY KEY,
    user_id                 UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider                VARCHAR(20)  NOT NULL,
    external_id             VARCHAR(100) NOT NULL,
    handle                  VARCHAR(200) NOT NULL,
    display_name            VARCHAR(200) NOT NULL,
    profile_url             VARCHAR(500) NOT NULL,
    avatar_url              VARCHAR(1000),
    followers               BIGINT       NOT NULL,
    total_views             BIGINT,
    content_count           BIGINT,
    encrypted_refresh_token TEXT,
    needs_reconnect         BOOLEAN      NOT NULL DEFAULT FALSE,
    connected_at            TIMESTAMPTZ  NOT NULL,
    last_synced_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_social_accounts_provider_external UNIQUE (provider, external_id),
    CONSTRAINT uk_social_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_social_accounts_provider CHECK (provider IN ('TWITCH', 'YOUTUBE'))
);
