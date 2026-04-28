# GrayFile

**Meter. Trace. Close.**

GrayFile is an auditable billing and metering gateway for OpenAI-compatible LLM inference backends (vLLM, NVIDIA NIM, and compatible APIs).

## What is implemented (V1 bootstrap)

- Quarkus gateway endpoint: `POST /llm/v1/chat/completions`
- OpenAI-compatible proxy forwarding to a configured backend endpoint
- Usage extraction from backend response (`usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens`)
- PostgreSQL persistence for:
  - `usage_events`
  - `billing_windows`
- Billing window engine per scope (`customer_id`, `api_key_id`, `model`)
  - closes on first threshold reached: **10 minutes** or **1000 tokens**
  - token overflow carry-forward to next window
- Scheduled time-based closure for stale open windows
- Prometheus metrics via Quarkus `/q/metrics`
- Docker Compose stack with gateway, PostgreSQL, mock OpenAI-compatible backend, Prometheus, and Grafana

## Repository structure

```text
grayfile/
├── README.md
├── docs/
├── gateway/
├── deploy/
└── scripts/
```

## Prerequisites

- Java 25+
- Maven 3.9+
- Docker + Docker Compose

## Local build

```bash
./mvnw -pl gateway -am clean test
```

## Run locally (without Docker)

1. Start PostgreSQL and set connection env vars:
   - `GRAYFILE_DB_URL`
   - `GRAYFILE_DB_USER`
   - `GRAYFILE_DB_PASSWORD`
2. Set backend URL:
   - `GRAYFILE_BACKEND_URL` (e.g. `http://localhost:8000`)
3. Run Quarkus:

```bash
./mvnw -pl gateway quarkus:dev
```

## Run with Docker Compose

```bash
docker compose -f deploy/docker-compose.yml up -d --build
```

## Deploy on Kubernetes

Kubernetes manifests and a step-by-step deployment guide are available in `deploy/kubernetes/`.

```bash
kubectl kustomize deploy/kubernetes/managed-postgres
kubectl kustomize deploy/kubernetes/postgres-statefulset
kubectl kustomize deploy/kubernetes/demo-mock-backend
```

Services:

- Gateway (direct, local only): `http://localhost:8080`
- Management UI: `http://localhost:4200`
- Envoy public listener (`/llm/v1/*`): `http://localhost:11000` (rate limits + overload protection)
- Envoy restricted listener (`/management/v1/*`, internal/VPN intended): `http://localhost:11001`
- ext_proc service (usage extraction for egress responses): `localhost:18080`
- PostgreSQL: `localhost:5432`
- Mock backend: `http://localhost:8000`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Observability:

- Prometheus scrapes the gateway metrics endpoint at `http://localhost:8080/q/metrics`
- Grafana is pre-provisioned with a default `Prometheus` datasource
- Grafana credentials: `admin` / `admin`
- A dashboard named `GrayFile Overview` is auto-loaded in the `GrayFile` folder

## Example request

```bash
curl -X POST http://localhost:11000/llm/v1/chat/completions \
  -H 'content-type: application/json' \
  -H 'x-customer-id: customer-1' \
  -H 'x-api-key-id: key-1' \
  -H 'x-llm-model: facebook/opt-125m' \
  -d '{
    "model": "facebook/opt-125m",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```


## Rate limits and overload protection

Envoy enforces throttling for `POST /llm/v1/chat/completions` using partition keys:

- `x-customer-id`
- `x-api-key-id`
- `x-llm-model`

Baseline defaults (local/static policy):

- **Default**: 240 requests/minute per Envoy instance for chat completions.
- **Tenant override `tenant-enterprise`**: 600 requests/minute.
- **Tenant override `tenant-free`**: 60 requests/minute.

Overload protection:

- Envoy applies local token-bucket throttling on the public LLM listener.
- Envoy also caps in-flight load toward Quarkus with gateway cluster circuit breakers (`max_requests: 300`, `max_pending_requests: 250`).
- This protects Quarkus worker saturation and reduces cascading pressure on PostgreSQL writes.

Throttling response contract:

- HTTP status `429 Too Many Requests`
- JSON error body with `error.code = rate_limited`
- Headers: `x-throttle-reason`, `retry-after`, `x-rate-limit-partition-keys`, and standard `X-RateLimit-*` headers emitted by Envoy.

Rationale:

- Keep the edge policy deterministic and transparent for client implementers.
- Provide per-tenant headroom for premium plans while preserving a safe default.
- Fail fast at the edge rather than degrading Quarkus + PostgreSQL under burst traffic.

## Container smoke test

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-containers.ps1
```

## ext_proc benchmark rapide (p95/p99)

```bash
java scripts/MeasureExtProcOverhead.java \
  --baseline-url http://localhost:8080/llm/v1/chat/completions \
  --ext-proc-url http://localhost:11000/llm/v1/chat/completions \
  --iterations 500
```

## Billing behavior summary

- A billing window is opened per `(customer_id, api_key_id, model)`.
- A window closes when either:
  - it reaches 1000 tokens, or
  - 10 minutes elapsed since window start.
- Token overflow is automatically moved into newly created windows.

## Notes

- V1 focuses on metering correctness and auditability.
- Invoice-line generation and advanced pricing models remain out of scope for this phase.
