# GrayFile Architecture

## Overview

GrayFile acts as a control point between client traffic and inference backends.

It forwards OpenAI-compatible requests, extracts usage data from backend responses, persists metering events, and applies billing window logic before forwarding the response back to the caller.

## Logical architecture

```text
Clients
  -> GrayFile Gateway
      -> Envoy (egress resilience hop)
          -> Inference Backend (vLLM, NIM, other OpenAI-compatible API)
      -> PostgreSQL
      -> Prometheus / Grafana
```

## Main responsibilities

### Gateway layer
- Expose OpenAI-compatible endpoints
- Authenticate and identify the caller
- Forward requests to the selected inference backend
- Normalize backend responses where needed

### Metering layer
- Extract usage fields from backend responses
- Persist raw usage events
- Correlate usage with customer, API key, model, and request ID
- Feed the billing window engine

### Billing window engine
- Maintain one active billing window per billing scope
- Close a window when the first threshold is reached
- Open the next billing window immediately after closure
- Carry token overflow into the next window when required

### Persistence layer
- Store usage events
- Store billing windows
- Store future invoice lines and billing exports

### Observability layer
- Expose application metrics
- Correlate gateway latency with backend latency
- Track billing window closures and usage volume

## Suggested billing scope

The default billing scope should be:

- customer_id
- api_key_id
- model

This keeps billing deterministic and auditable while allowing model-specific pricing later.

## Suggested services

### grayfile-gateway
Main Quarkus HTTP service.

### PostgreSQL
System of record for usage events and billing windows.

### Prometheus
Metrics scraping for both gateway and inference backend.

### Grafana
Dashboards for operational visibility.

## Core request flow

1. Client sends an OpenAI-compatible request to GrayFile
2. GrayFile authenticates and resolves billing identity
3. GrayFile forwards the request to the inference backend
4. GrayFile receives the response
5. GrayFile extracts usage information
6. GrayFile persists a usage event
7. GrayFile updates or closes the billing window
8. GrayFile returns the response to the client


### LLM egress hop (Envoy)
- Envoy is the single network resilience layer between the gateway and LLM backend.
- Quarkus keeps business logic only (validation, usage capture, metering persistence) and does not duplicate retries/circuit-breaker logic.
- Envoy policy (see `deploy/envoy/envoy.yaml`):
  - Request timeout: **5s** total for the hop.
  - Retry budget: **2 retries max** (3 total attempts) on explicit conditions only: connection failures, refused stream, gateway errors, `5xx`, and explicit retriable status codes `502/503/504`.
  - Per-try timeout: **2s** to bound each retry attempt.
  - Circuit breaker limits: max connections **100**, max pending requests **100**, max requests **200**.
  - Outlier detection: eject upstream after **5 consecutive 5xx**, check every **10s**, base ejection **30s**, cap ejection at **50%** of hosts.

### SLA and fallback strategy for this hop
- End-to-end budget for gateway -> Envoy -> backend: **5s** max before returning an error upstream.
- Retry budget is bounded and deterministic (at most 2 retries), preventing unbounded tail-latency amplification.
- Fallback strategy in V1:
  - No secondary backend failover yet.
  - On timeout / retries exhausted / open circuit, GrayFile returns backend error to caller and records metering only when a valid usage payload exists.

## Key integration points

### Backend integration
GrayFile expects an OpenAI-compatible backend response with a `usage` object containing:
- `prompt_tokens`
- `completion_tokens`
- `total_tokens`

### Database integration
PostgreSQL is the source of truth for:
- usage events
- billing windows
- future invoice artifacts

### Observability integration
Prometheus metrics should expose:
- request counts
- request latency
- billing windows closed
- billable tokens
- backend error rates

## Non-goals for V1

- Full invoice generation
- Multi-tenant RBAC
- Advanced pricing rules
- Streaming reconciliation edge cases
- External ERP integration

## V1 implementation bias

The initial implementation should favor:
- correctness over throughput
- auditability over cleverness
- explicit state transitions
- clear persistence semantics
