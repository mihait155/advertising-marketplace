ALTER TABLE postings
    ADD COLUMN status   VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN pay_from NUMERIC(12, 2),
    ADD COLUMN pay_to   NUMERIC(12, 2),
    ADD CONSTRAINT ck_postings_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    ADD CONSTRAINT ck_postings_pay_non_negative CHECK ((pay_from IS NULL OR pay_from >= 0) AND (pay_to IS NULL OR pay_to >= 0)),
    ADD CONSTRAINT ck_postings_pay_range CHECK (pay_from IS NULL OR pay_to IS NULL OR pay_from <= pay_to);

ALTER TABLE postings
    ALTER COLUMN status DROP DEFAULT;

CREATE INDEX idx_postings_status_created_at ON postings (status, created_at DESC);

CREATE TABLE offers
(
    id         UUID PRIMARY KEY,
    posting_id UUID           NOT NULL REFERENCES postings (id) ON DELETE CASCADE,
    offerer_id UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT           NOT NULL,
    status     VARCHAR(10)    NOT NULL,
    price_from NUMERIC(12, 2) NOT NULL,
    price_to   NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_offers_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_offers_price_range CHECK (price_from >= 0 AND price_from <= price_to)
);

CREATE INDEX idx_offers_posting_id_created_at ON offers (posting_id, created_at DESC);
CREATE INDEX idx_offers_offerer_id_created_at ON offers (offerer_id, created_at DESC);
CREATE UNIQUE INDEX uk_offers_one_pending_per_offerer ON offers (posting_id, offerer_id) WHERE status = 'PENDING';
