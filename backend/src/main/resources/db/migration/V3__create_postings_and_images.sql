CREATE TABLE postings
(
    id         UUID PRIMARY KEY,
    creator_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category   VARCHAR(30)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_postings_category CHECK (category IN ('MARKETER_POSTING', 'CUSTOMER_POSTING'))
);

CREATE INDEX idx_postings_category_created_at ON postings (category, created_at DESC);
CREATE INDEX idx_postings_creator_id ON postings (creator_id);

CREATE TABLE images
(
    id             UUID PRIMARY KEY,
    owner_id       UUID         REFERENCES users (id) ON DELETE SET NULL,
    posting_id     UUID         REFERENCES postings (id) ON DELETE SET NULL,
    storage_key    VARCHAR(255) NOT NULL,
    content_type   VARCHAR(50)  NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    sha256         VARCHAR(64)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    orphaned_since TIMESTAMPTZ,
    CONSTRAINT uk_images_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_images_posting_id ON images (posting_id);
CREATE INDEX idx_images_orphaned ON images (orphaned_since) WHERE posting_id IS NULL;
