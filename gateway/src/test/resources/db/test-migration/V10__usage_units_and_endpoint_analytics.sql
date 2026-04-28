ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS endpoint_type VARCHAR(255);

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS input_tokens INTEGER;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS output_tokens INTEGER;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS billable_unit_type VARCHAR(255);

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS billable_unit_count DECIMAL(18,6);

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS usage_raw CLOB;

UPDATE usage_events
SET input_tokens = prompt_tokens
WHERE input_tokens IS NULL;

UPDATE usage_events
SET output_tokens = completion_tokens
WHERE output_tokens IS NULL;

UPDATE usage_events
SET total_tokens = CASE
    WHEN COALESCE(total_tokens, 0) > 0 THEN total_tokens
    ELSE COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)
END
WHERE total_tokens <= 0
  AND (input_tokens IS NOT NULL OR output_tokens IS NOT NULL);

UPDATE usage_events
SET endpoint_type = 'token'
WHERE endpoint_type IS NULL OR endpoint_type = '';

UPDATE usage_events
SET billable_unit_type = CASE
    WHEN COALESCE(total_tokens, 0) > 0 THEN 'tokens'
    ELSE 'requests'
END
WHERE billable_unit_type IS NULL OR billable_unit_type = '';

UPDATE usage_events
SET billable_unit_count = CASE
    WHEN billable_unit_type = 'tokens' THEN CAST(COALESCE(total_tokens, 0) AS DECIMAL(18,6))
    ELSE CAST(1 AS DECIMAL(18,6))
END
WHERE billable_unit_count IS NULL;

ALTER TABLE usage_events
    ALTER COLUMN endpoint_type SET DEFAULT 'token';
ALTER TABLE usage_events
    ALTER COLUMN billable_unit_type SET DEFAULT 'tokens';
ALTER TABLE usage_events
    ALTER COLUMN billable_unit_count SET DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_usage_events_endpoint_unit_analytics
    ON usage_events (event_time DESC, endpoint_type, billable_unit_type, customer_id, model_name);
