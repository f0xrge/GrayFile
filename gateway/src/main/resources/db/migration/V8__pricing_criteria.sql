ALTER TABLE llm_models
    ADD COLUMN IF NOT EXISTS default_time_criterion_seconds INTEGER;

ALTER TABLE llm_models
    ADD COLUMN IF NOT EXISTS default_token_criterion INTEGER;

UPDATE llm_models
SET default_time_criterion_seconds = COALESCE(default_time_criterion_seconds, 1),
    default_token_criterion = COALESCE(default_token_criterion, 1000);

ALTER TABLE llm_models
    ALTER COLUMN default_time_criterion_seconds SET NOT NULL;

ALTER TABLE llm_models
    ALTER COLUMN default_time_criterion_seconds SET DEFAULT 1;

ALTER TABLE llm_models
    ALTER COLUMN default_token_criterion SET NOT NULL;

ALTER TABLE llm_models
    ALTER COLUMN default_token_criterion SET DEFAULT 1000;

ALTER TABLE customer_model_pricing
    ADD COLUMN IF NOT EXISTS time_criterion_seconds INTEGER;

ALTER TABLE customer_model_pricing
    ADD COLUMN IF NOT EXISTS token_criterion INTEGER;

UPDATE customer_model_pricing
SET time_criterion_seconds = COALESCE(time_criterion_seconds, 1),
    token_criterion = COALESCE(token_criterion, 1000);

ALTER TABLE customer_model_pricing
    ALTER COLUMN time_criterion_seconds SET NOT NULL;

ALTER TABLE customer_model_pricing
    ALTER COLUMN time_criterion_seconds SET DEFAULT 1;

ALTER TABLE customer_model_pricing
    ALTER COLUMN token_criterion SET NOT NULL;

ALTER TABLE customer_model_pricing
    ALTER COLUMN token_criterion SET DEFAULT 1000;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS billed_time_criterion_seconds INTEGER;

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS billed_token_criterion INTEGER;

UPDATE usage_events
SET billed_time_criterion_seconds = COALESCE(billed_time_criterion_seconds, 1),
    billed_token_criterion = COALESCE(billed_token_criterion, 1000);

ALTER TABLE usage_events
    ALTER COLUMN billed_time_criterion_seconds SET NOT NULL;

ALTER TABLE usage_events
    ALTER COLUMN billed_time_criterion_seconds SET DEFAULT 1;

ALTER TABLE usage_events
    ALTER COLUMN billed_token_criterion SET NOT NULL;

ALTER TABLE usage_events
    ALTER COLUMN billed_token_criterion SET DEFAULT 1000;
