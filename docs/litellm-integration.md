# GrayFile + LiteLLM Integration

## Target split

GrayFile is the business control plane. It remains authoritative for customers,
API keys, public model IDs, pricing, billing windows, usage analytics, and audit
events.

LiteLLM is the internal execution/provider plane. It owns provider abstraction,
provider-specific deployments, fallbacks, and virtual-key enforcement used by the
proxy.

The management UI always calls GrayFile. It never calls LiteLLM directly.

## Runtime modes

`GRAYFILE_BACKEND_MODE` controls request forwarding:

- `direct`: existing behavior. GrayFile resolves `model_routes` and calls the
  selected backend.
- `litellm`: GrayFile validates scope and forwards to the internal LiteLLM proxy
  at `GRAYFILE_LITELLM_BASE_URL` using `GRAYFILE_LITELLM_MASTER_KEY`.

In both modes, GrayFile captures usage from the backend response and calculates
official customer cost. LiteLLM spend is operational telemetry only.

## Synchronization contract

GrayFile persists first, audits the change, then syncs LiteLLM resources.

Tracked resources are stored in `litellm_resources`:

- `model_route -> model_deployment`
- `api_key -> virtual_key`

Sync states:

- `pending`: GrayFile state changed and needs provisioning.
- `synced`: LiteLLM accepted the provisioning request.
- `failed`: provisioning failed and can be retried.
- `disabled`: virtual key has been blocked/disabled.

Default local settings keep sync safe:

- `GRAYFILE_LITELLM_SYNC_ENABLED=false`
- `GRAYFILE_LITELLM_SYNC_DRY_RUN=true`

Enable real provisioning only after setting a real `LITELLM_MASTER_KEY`,
provider credentials, and a LiteLLM database.

## LiteLLM admin endpoints used

The integration wraps these LiteLLM proxy admin endpoints:

- `GET /health`
- `POST /model/new`
- `POST /key/generate`
- `POST /key/block`

The client is isolated in `LiteLlmAdminClient` so endpoint/payload adjustments
for a pinned LiteLLM version stay localized.

## Local run

Direct mode:

```bash
docker compose -f deploy/docker-compose.yml up -d --build
```

LiteLLM runtime mode:

```bash
GRAYFILE_BACKEND_MODE=litellm \
GRAYFILE_LITELLM_SYNC_ENABLED=true \
GRAYFILE_LITELLM_SYNC_DRY_RUN=false \
docker compose -f deploy/docker-compose.yml --profile inference up -d --build
```

For existing local Postgres volumes, create the LiteLLM database manually if the
init script has already run once:

```sql
CREATE DATABASE litellm;
```
