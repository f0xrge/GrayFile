CREATE TABLE IF NOT EXISTS usage_events (
    id UUID PRIMARY KEY,
    request_id TEXT NOT NULL,
    customer_id TEXT NOT NULL,
    api_key_id TEXT NOT NULL,
    model_name TEXT NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    prompt_tokens INTEGER NOT NULL,
    completion_tokens INTEGER NOT NULL,
    total_tokens INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_usage_events_scope_time
    ON usage_events (customer_id, api_key_id, model_name, event_time);

CREATE TABLE IF NOT EXISTS billing_windows (
    id UUID PRIMARY KEY,
    customer_id TEXT NOT NULL,
    api_key_id TEXT NOT NULL,
    model_name TEXT NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ,
    token_total INTEGER NOT NULL,
    closure_reason TEXT,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_windows_one_active
    ON billing_windows (customer_id, api_key_id, model_name)
    WHERE active = TRUE;
