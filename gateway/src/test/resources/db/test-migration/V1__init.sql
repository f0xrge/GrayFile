CREATE TABLE IF NOT EXISTS usage_events (
    id UUID PRIMARY KEY,
    request_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    api_key_id VARCHAR(255) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    prompt_tokens INTEGER NOT NULL,
    completion_tokens INTEGER NOT NULL,
    total_tokens INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_usage_events_scope_time
    ON usage_events (customer_id, api_key_id, model_name, event_time);

CREATE TABLE IF NOT EXISTS billing_windows (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    api_key_id VARCHAR(255) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end TIMESTAMP WITH TIME ZONE,
    token_total INTEGER NOT NULL,
    closure_reason VARCHAR(255),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_billing_windows_scope_active
    ON billing_windows (customer_id, api_key_id, model_name, active);
