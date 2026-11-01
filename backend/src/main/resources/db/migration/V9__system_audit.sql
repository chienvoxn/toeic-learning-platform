
-- =====================================================
-- V9: SYSTEM & AUDIT
-- TOEIC Learning Platform
-- =====================================================

-- 1. AUDIT LOGS
CREATE TABLE audit_logs (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_audit_logs_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users(user_id)
);

-- 2. SYSTEM SETTINGS
CREATE TABLE system_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value JSONB NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_system_settings_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(user_id)
);

-- 3. NOTIFICATIONS
CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY,
    notification_type VARCHAR(60) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    payload_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. USER NOTIFICATIONS
CREATE TABLE user_notifications (
    user_notification_id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    user_id UUID NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_notifications_notification
        FOREIGN KEY (notification_id)
        REFERENCES notifications(notification_id),

    CONSTRAINT fk_user_notifications_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT uq_user_notification
        UNIQUE (user_id, notification_id)
);

-- 5. OUTBOX EVENTS
CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT chk_outbox_retry_count
        CHECK (retry_count >= 0)
);

-- =====================================================
-- INDEXES
-- =====================================================

CREATE INDEX idx_audit_logs_actor
    ON audit_logs(actor_user_id);

CREATE INDEX idx_audit_logs_entity
    ON audit_logs(entity_type, entity_id);

CREATE INDEX idx_audit_logs_created_at
    ON audit_logs(created_at DESC);

CREATE INDEX idx_system_settings_updated_by
    ON system_settings(updated_by);

CREATE INDEX idx_user_notifications_user
    ON user_notifications(user_id);

CREATE INDEX idx_user_notifications_status
    ON user_notifications(user_id, delivery_status);

CREATE INDEX idx_user_notifications_notification
    ON user_notifications(notification_id);

CREATE INDEX idx_outbox_events_status
    ON outbox_events(status, occurred_at);

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events(aggregate_type, aggregate_id);
