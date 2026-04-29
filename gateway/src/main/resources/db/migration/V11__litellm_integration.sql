ALTER TABLE model_routes
    ADD COLUMN IF NOT EXISTS deployment_id TEXT,
    ADD COLUMN IF NOT EXISTS provider TEXT,
    ADD COLUMN IF NOT EXISTS litellm_model TEXT,
    ADD COLUMN IF NOT EXISTS api_base TEXT,
    ADD COLUMN IF NOT EXISTS api_version TEXT,
    ADD COLUMN IF NOT EXISTS secret_ref TEXT,
    ADD COLUMN IF NOT EXISTS last_sync_status TEXT NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS last_sync_error TEXT,
    ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMPTZ;

UPDATE model_routes
SET deployment_id = backend_id
WHERE deployment_id IS NULL OR deployment_id = '';

UPDATE model_routes
SET litellm_model = model_id
WHERE litellm_model IS NULL OR litellm_model = '';

UPDATE model_routes
SET api_base = base_url
WHERE api_base IS NULL OR api_base = '';

CREATE TABLE IF NOT EXISTS litellm_resources (
    id UUID PRIMARY KEY,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    litellm_resource_type TEXT NOT NULL,
    litellm_resource_id TEXT,
    last_sync_status TEXT NOT NULL DEFAULT 'pending',
    last_sync_error TEXT,
    last_synced_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_litellm_resources_entity_resource
    ON litellm_resources (entity_type, entity_id, litellm_resource_type);

CREATE INDEX IF NOT EXISTS idx_litellm_resources_status
    ON litellm_resources (last_sync_status, updated_at DESC);
