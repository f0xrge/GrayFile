ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS contract_version TEXT NOT NULL DEFAULT 'usage_extraction.v1';

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS extractor_version TEXT NOT NULL DEFAULT 'gateway-backend-payload-v1';

ALTER TABLE usage_events
    ADD COLUMN IF NOT EXISTS usage_signature TEXT NOT NULL DEFAULT '';
