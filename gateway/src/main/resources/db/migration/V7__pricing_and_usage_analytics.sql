ALTER TABLE llm_models
    ADD COLUMN IF NOT EXISTS default_time_price NUMERIC(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS default_token_price NUMERIC(18,6) NOT NULL DEFAULT 0;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS billed_time_price NUMERIC(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS billed_token_price NUMERIC(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS time_cost NUMERIC(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS token_cost NUMERIC(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_cost NUMERIC(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pricing_source TEXT NOT NULL DEFAULT 'model-default';

CREATE TABLE IF NOT EXISTS customer_model_pricing (
    id UUID PRIMARY KEY,
    customer_id TEXT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    model_id TEXT NOT NULL REFERENCES llm_models(id) ON DELETE CASCADE,
    time_price NUMERIC(18,6) NOT NULL,
    token_price NUMERIC(18,6) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_model_pricing_scope
    ON customer_model_pricing (customer_id, model_id);

CREATE INDEX IF NOT EXISTS idx_usage_events_analytics_scope
    ON usage_events (event_time DESC, customer_id, model_name);
