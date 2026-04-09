CREATE TABLE IF NOT EXISTS model_routes (
    model_id TEXT NOT NULL REFERENCES llm_models(id) ON DELETE CASCADE,
    backend_id TEXT NOT NULL,
    base_url TEXT NOT NULL,
    weight INTEGER NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (model_id, backend_id)
);

CREATE INDEX IF NOT EXISTS idx_model_routes_lookup
    ON model_routes (model_id, active, weight DESC, backend_id);
