CREATE UNIQUE INDEX IF NOT EXISTS ux_usage_events_request_customer_key_model
    ON usage_events (request_id, customer_id, api_key_id, model_name);
