ALTER TABLE llm_models
    ADD COLUMN IF NOT EXISTS default_time_price DECIMAL(18,6) NOT NULL DEFAULT 0;

ALTER TABLE llm_models
    ADD COLUMN IF NOT EXISTS default_token_price DECIMAL(18,6) NOT NULL DEFAULT 0;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS billed_time_price DECIMAL(18,6) NOT NULL DEFAULT 0;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS billed_token_price DECIMAL(18,6) NOT NULL DEFAULT 0;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS time_cost DECIMAL(18,6) NOT NULL DEFAULT 0;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS token_cost DECIMAL(18,6) NOT NULL DEFAULT 0;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS total_cost DECIMAL(18,6) NOT NULL DEFAULT 0;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS pricing_source VARCHAR(255) NOT NULL DEFAULT 'model-default';

CREATE TABLE IF NOT EXISTS customer_model_pricing (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    model_id VARCHAR(255) NOT NULL,
    time_price DECIMAL(18,6) NOT NULL,
    token_price DECIMAL(18,6) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_model_pricing_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT fk_customer_model_pricing_model FOREIGN KEY (model_id) REFERENCES llm_models(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_model_pricing_scope
    ON customer_model_pricing (customer_id, model_id);

CREATE INDEX IF NOT EXISTS idx_usage_events_analytics_scope
    ON usage_events (event_time DESC, customer_id, model_name);
