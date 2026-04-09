DELETE FROM usage_events duplicate
USING usage_events kept
WHERE duplicate.request_id = kept.request_id
  AND duplicate.customer_id = kept.customer_id
  AND duplicate.api_key_id = kept.api_key_id
  AND duplicate.model_name = kept.model_name
  AND duplicate.id > kept.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_usage_events_request_customer_key_model
    ON usage_events (request_id, customer_id, api_key_id, model_name);
