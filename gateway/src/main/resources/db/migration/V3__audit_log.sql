CREATE TABLE IF NOT EXISTS audit_log (
    event_id BIGSERIAL PRIMARY KEY,
    event_type TEXT NOT NULL,
    actor TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    payload_json JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    prev_hash TEXT,
    event_hash TEXT NOT NULL,
    signature TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_log_occurred_at
    ON audit_log (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity
    ON audit_log (entity_type, entity_id, occurred_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grayfile_audit_writer') THEN
        CREATE ROLE grayfile_audit_writer NOLOGIN;
    END IF;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping role creation for grayfile_audit_writer due to insufficient privilege';
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grayfile_audit_writer') THEN
        EXECUTE 'GRANT INSERT, SELECT ON audit_log TO grayfile_audit_writer';

        ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

        DROP POLICY IF EXISTS audit_log_select_policy ON audit_log;
        CREATE POLICY audit_log_select_policy
            ON audit_log FOR SELECT
            TO grayfile_audit_writer
            USING (true);

        DROP POLICY IF EXISTS audit_log_insert_policy ON audit_log;
        CREATE POLICY audit_log_insert_policy
            ON audit_log FOR INSERT
            TO grayfile_audit_writer
            WITH CHECK (true);
    END IF;
END $$;

REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM PUBLIC;

CREATE OR REPLACE FUNCTION prevent_audit_log_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;

DROP TRIGGER IF EXISTS trg_prevent_audit_log_update ON audit_log;
CREATE TRIGGER trg_prevent_audit_log_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_mutation();

DROP TRIGGER IF EXISTS trg_prevent_audit_log_delete ON audit_log;
CREATE TRIGGER trg_prevent_audit_log_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_mutation();

CREATE TABLE IF NOT EXISTS audit_export_state (
    id SMALLINT PRIMARY KEY,
    last_exported_event_id BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO audit_export_state (id, last_exported_event_id)
VALUES (1, 0)
ON CONFLICT (id) DO NOTHING;
