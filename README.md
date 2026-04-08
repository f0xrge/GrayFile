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

Services:

- Gateway: `http://localhost:8080`
- PostgreSQL: `localhost:5432`
- Mock backend: `http://localhost:8000`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## Example request

```bash
curl -X POST http://localhost:8080/llm/v1/chat/completions \
  -H 'content-type: application/json' \
  -H 'x-customer-id: customer-1' \
  -H 'x-api-key-id: key-1' \
  -d '{
    "model": "facebook/opt-125m",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

## Container smoke test

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-containers.ps1
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
