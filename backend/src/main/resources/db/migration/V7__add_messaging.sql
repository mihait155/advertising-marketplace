CREATE TABLE conversations
(
    id                     UUID PRIMARY KEY,
    posting_id             UUID        NOT NULL REFERENCES postings (id) ON DELETE CASCADE,
    creator_id             UUID REFERENCES users (id) ON DELETE SET NULL,
    counterpart_id         UUID REFERENCES users (id) ON DELETE SET NULL,
    counterpart_key        UUID        NOT NULL,
    creator_last_read_at     TIMESTAMPTZ,
    counterpart_last_read_at TIMESTAMPTZ,
    creator_notified_up_to     TIMESTAMPTZ,
    counterpart_notified_up_to TIMESTAMPTZ,
    last_message_id        UUID,
    last_message_at        TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_conversations_posting_counterpart UNIQUE (posting_id, counterpart_key)
);

CREATE INDEX idx_conversations_creator ON conversations (creator_id, last_message_at DESC);
CREATE INDEX idx_conversations_counterpart ON conversations (counterpart_id, last_message_at DESC);

CREATE TABLE messages
(
    id              UUID PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    type            VARCHAR(10) NOT NULL,
    system_event    VARCHAR(30),
    offer_id        UUID REFERENCES offers (id) ON DELETE SET NULL,
    body            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT ck_messages_type CHECK (type IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_messages_system_event CHECK ((type = 'SYSTEM') = (system_event IS NOT NULL))
);

CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at DESC, id DESC);
CREATE INDEX idx_messages_sender_created ON messages (sender_id, created_at DESC);

ALTER TABLE conversations
    ADD CONSTRAINT fk_conversations_last_message FOREIGN KEY (last_message_id) REFERENCES messages (id) ON DELETE SET NULL;

ALTER TABLE images
    ADD COLUMN message_id UUID REFERENCES messages (id) ON DELETE SET NULL;

CREATE INDEX idx_images_message_id ON images (message_id);
DROP INDEX idx_images_orphaned;
CREATE INDEX idx_images_orphaned ON images (orphaned_since) WHERE posting_id IS NULL AND message_id IS NULL;

CREATE TABLE user_blocks
(
    blocker_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_user_blocks_not_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id);

CREATE TABLE message_reports
(
    id          UUID PRIMARY KEY,
    message_id  UUID         NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    reporter_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      VARCHAR(1000) NOT NULL,
    status      VARCHAR(10)  NOT NULL,
    resolution  VARCHAR(20),
    resolved_by UUID REFERENCES users (id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_message_reports_message_reporter UNIQUE (message_id, reporter_id),
    CONSTRAINT ck_message_reports_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_message_reports_status_created ON message_reports (status, created_at);

ALTER TABLE users
    ADD COLUMN email_message_notifications BOOLEAN NOT NULL DEFAULT TRUE;
