# GrayFile Architecture

## Overview

GrayFile acts as a control point between client traffic and inference backends.

It forwards OpenAI-compatible requests, extracts usage data from backend responses, persists metering events, and applies billing window logic before forwarding the response back to the caller.

## Logical architecture

```text
Clients
  -> Envoy (edge + egress policies)
      -> GrayFile Gateway
          -> Inference Backend (vLLM, NIM, other OpenAI-compatible API)
      -> PostgreSQL
      -> Prometheus / Grafana
```

## Explicit two-layer responsibility model

### Layer 1 — Envoy (network and L7 policy plane)
- Routing (public vs management listeners, upstream cluster selection)
- TLS termination and upstream TLS policy
- Retries / per-try timeout / total timeout budgets
- Circuit breaker and outlier detection
- Rate limiting (edge throttling and overload absorption)
- L7 observability (status codes, latency buckets, retry/circuit/throttle signals)

### Layer 2 — GrayFile (business and metering plane)
- Billing scope validation (`customer_id`, `api_key_id`, `model`)
- Usage extraction from backend payloads (`usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens`)
- Metering event persistence in PostgreSQL
- Billing window lifecycle management (open/close/carry-over logic)

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

## GrayFile-only business invariants (must not move to Envoy)

The following invariants stay strictly in GrayFile to preserve correctness and auditability:

1. **Auditability invariant**
   - Every billable decision must be traceable to persisted domain data (`request_id`, billing scope, model, raw usage, resulting window transition).
   - Edge proxies may enforce transport policy, but only GrayFile can authoritatively attach business identity and produce auditable billing records.

2. **DB atomicity invariant**
   - Usage event persistence and billing-window transition must be committed atomically in the same transactional boundary.
   - No partial state is allowed (e.g., event persisted without corresponding window update, or window change without event lineage).
   - Retry/replay handling must preserve idempotency semantics at the GrayFile persistence boundary.

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

## Request path flow with Envoy decision points

```text
Client
  |
  v
[Envoy ingress listener]
  |- Decision A: listener/path match?
  |    |- no -> 404
  |    '- yes
  |- Decision B (management only): JWT valid?
  |    |- no -> 401
  |    '- yes
  |- Decision C (management only): RBAC identity/IP allowed?
  |    |- no -> 403
  |    '- yes
  |- Decision D (public LLM only): local rate limit exceeded?
  |    |- yes -> 429 (throttled at edge)
  |    '- no
  v
[GrayFile]
  |- Decision E: billing scope headers valid?
  |    |- no -> 4xx (validation error)
  |    '- yes
  |- Forward to Envoy egress cluster for backend call
  v
[Envoy egress]
  |- Decision F: retryable failure?
  |    |- yes -> retry (bounded budget)
  |    '- no
  |- Decision G: circuit open / timeout budget exceeded?
  |    |- yes -> upstream error to GrayFile
  |    '- no -> backend response
  v
[GrayFile]
  |- Decision H: usage payload present/valid?
  |    |- no -> return response/error without billable write
  |    '- yes
  |- Atomic DB transaction:
  |    1) persist usage event
  |    2) update/close/open billing window
  v
Client response
```

## Incremental migration checklist (Envoy-first, then tuning, then scale split)

### Phase 1 — Edge policies first (functional parity)
- [ ] Route all `/llm/v1/*` and `/management/v1/*` through Envoy listeners with explicit path isolation.
- [ ] Enable TLS + JWT/RBAC on management ingress.
- [ ] Enable baseline local rate limit on public LLM ingress.
- [ ] Keep GrayFile business logic unchanged (scope validation, usage extraction, billing persistence).
- [ ] Confirm no billing invariant moved out of GrayFile.

### Phase 2 — Policy tuning and SLO hardening
- [ ] Tune retry conditions, per-try timeout, and total timeout to match target latency SLOs.
- [ ] Tune circuit breaker and outlier detection thresholds with production telemetry.
- [ ] Tune rate-limit descriptors/quotas by tenant tier and model profile.
- [ ] Add dashboards/alerts for 401/403/404/429, retries, ejections, and tail latency.
- [ ] Run controlled load and failure-injection tests before each threshold change.

### Phase 3 — Separation of scale planes
- [ ] Scale Envoy independently for edge traffic patterns (burst handling, TLS/L7 policy throughput).
- [ ] Scale GrayFile independently for business workload patterns (DB writes, billing window churn).
- [ ] Introduce dedicated autoscaling signals:
  - Envoy: RPS, p95/p99 latency, 429 rate, retry volume.
  - GrayFile: DB transaction latency, write throughput, window transition rate.
- [ ] Validate that independent scaling does not violate GrayFile atomicity/auditability invariants.
- [ ] (Optional) externalize rate-limit service while preserving descriptor contract.


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



## Ingress throttling and overload policy

For `POST /llm/v1/chat/completions`, Envoy enforces an explicit edge throttling contract on the **public listener (`:11000`)**.

### Partition keys

Rate-limit descriptors are keyed by:

- `x-customer-id`
- `x-api-key-id`
- `x-llm-model`

This keeps throttling decisions aligned with the same tenancy/model axes used for metering and billing scopes.

### Default limits and tenant overrides (static V1 policy)

- Default chat-completions bucket: **240 requests/minute** per Envoy instance.
- Override for `tenant-enterprise`: **600 requests/minute**.
- Override for `tenant-free`: **60 requests/minute**.

> V1 uses static descriptors in Envoy local rate limit configuration. The same descriptor shape can later be migrated to a centralized rate-limit service without changing client headers.

### Overload protection toward Quarkus/PostgreSQL

Two controls are combined:

1. **Local rate limiting** on ingress to absorb bursts before they hit Quarkus.
2. **Cluster circuit-breaker caps** on `gateway_service` (`max_requests: 300`, `max_pending_requests: 250`, `max_connections: 200`) to bound concurrent pressure on gateway threads and downstream PostgreSQL writes.

### Throttling response and observability contract

When throttled, Envoy returns:

- HTTP **429 Too Many Requests**
- JSON body: `{"error":{"code":"rate_limited","message":"Request throttled by Envoy policy","type":"throttling"}}`
- Headers:
  - `x-throttle-reason: envoy_local_rate_limit`
  - `retry-after: 60`
  - `x-rate-limit-partition-keys: x-customer-id,x-api-key-id,x-llm-model`
  - Standard `X-RateLimit-*` headers

This makes client retry behavior explicit and simplifies dashboarding/alerting on throttling signals.

## Ingress security and route separation

Envoy now exposes **two distinct ingress listeners** in front of `grayfile-gateway`:

- **Public listener (`:11000`)**: only routes `/llm/v1/*` traffic.
- **Restricted management listener (`:11001`)**: only routes `/management/v1/*` traffic.

Any path mismatch on each listener returns `404` by policy.

### Management authN/authZ policy (Envoy)

For `/management/v1/*`, Envoy applies layered controls before forwarding to Quarkus:

1. **JWT/OIDC authentication (`envoy.filters.http.jwt_authn`)**
   - Requires a valid bearer token from the configured OIDC issuer.
   - Enforces audience `grayfile-management-api`.
2. **Authorization (`envoy.filters.http.rbac`)**
   - Requires explicit service identity header allow-list:
     - `x-service-identity: grayfile-admin-ui`
     - `x-service-identity: grayfile-ops-automation`
   - Requires caller source IP in approved internal/VPN CIDR ranges:
     - `10.0.0.0/8`
     - `172.16.0.0/12`
     - `192.168.0.0/16`
     - `100.64.0.0/10`

> In production, this identity header should be set by trusted infrastructure (or replaced by mTLS principal-based policy) and not be client-controlled.

## Access matrix

| Caller | Entry point | Path | Required identity | Decision |
|---|---|---|---|---|
| External API consumer | Public listener `:11000` | `/llm/v1/*` | Gateway app headers (`x-customer-id`, `x-api-key-id`) | **Allow** |
| External API consumer | Public listener `:11000` | `/management/v1/*` | N/A | **Deny (404)** |
| Internal admin UI / automation (VPN/internal network) | Restricted listener `:11001` | `/management/v1/*` | Valid OIDC JWT + allowed `x-service-identity` + allowed source CIDR | **Allow** |
| Internal caller missing JWT | Restricted listener `:11001` | `/management/v1/*` | Missing/invalid bearer token | **Deny (401)** |
| Internal caller with JWT but unauthorized identity/IP | Restricted listener `:11001` | `/management/v1/*` | JWT valid but identity or IP not allowed by RBAC | **Deny (403)** |
| Any caller | Restricted listener `:11001` | `/llm/v1/*` | N/A | **Deny (404)** |

## Integration validation scenarios (security)

Minimum scenarios to validate Envoy enforcement end-to-end:

1. **No token on management route => 401**
   ```bash
   curl -i http://localhost:11001/management/v1/customers
   ```

2. **Invalid token on management route => 401**
   ```bash
   curl -i http://localhost:11001/management/v1/customers \
     -H 'Authorization: Bearer invalid.token.value'
   ```

3. **Valid JWT but unauthorized service identity => 403**
   ```bash
   curl -i http://localhost:11001/management/v1/customers \
     -H 'Authorization: Bearer <valid_management_jwt>' \
     -H 'x-service-identity: unknown-service'
   ```

4. **Valid JWT and authorized identity from approved network => 200/2xx**
   ```bash
   curl -i http://localhost:11001/management/v1/customers \
     -H 'Authorization: Bearer <valid_management_jwt>' \
     -H 'x-service-identity: grayfile-admin-ui'
   ```

5. **Management path through public listener => 404**
   ```bash
   curl -i http://localhost:11000/management/v1/customers
   ```

6. **LLM path through restricted listener => 404**
   ```bash
   curl -i http://localhost:11001/llm/v1/chat/completions
   ```

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
