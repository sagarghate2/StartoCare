CREATE TABLE IF NOT EXISTS payment_orders (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    family_user_id UUID NOT NULL,
    booking_reference VARCHAR(32) NOT NULL UNIQUE,
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    razorpay_order_id VARCHAR(64) NOT NULL UNIQUE,
    razorpay_payment_id VARCHAR(64) UNIQUE,
    payment_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payment_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_payment_outbox_pending
    ON payment_outbox_events (published_at, created_at);
