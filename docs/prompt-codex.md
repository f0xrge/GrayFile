# Codex Handoff Prompt

Project name: GrayFile

GrayFile is a Quarkus-based billing and metering gateway for OpenAI-compatible LLM inference backends such as vLLM and NVIDIA NIM.

The gateway sits between clients and inference services. It proxies requests to backend inference APIs, captures usage information from responses, stores usage events in PostgreSQL, and applies billing window rules.

## Core billing rule

Close the current billing window when the first threshold is reached:

- 10 minutes elapsed since window start
- 1000 accumulated tokens

Immediately start the next billing window.

If a single response exceeds the token threshold, carry the overflow into the next window.

## Initial scope

Implement:

- `/llm/v1/chat/completions` as an OpenAI-compatible proxy
- extraction of `usage.prompt_tokens`, `usage.completion_tokens`, and `usage.total_tokens`
- persistence of usage events in PostgreSQL
- maintenance of open billing windows per `customer_id`, `api_key_id`, and `model`
- a scheduler to close expired windows
- Prometheus metrics for gateway activity and billing behavior
- Docker Compose deployment with PostgreSQL, Prometheus, Grafana, and a vLLM backend

## Preferred stack

- Quarkus
- PostgreSQL
- REST Client
- Micrometer / Prometheus
- Docker Compose

## Design principles

- audit-first
- backend-agnostic
- observable
- minimal coupling
- production-oriented structure even in PoC stage

## Suggested repository structure

```text
grayfile/
├── README.md
├── LICENSE
├── docs/
│   ├── architecture.md
│   ├── billing-model.md
│   └── prompt-codex.md
├── gateway/
├── deploy/
└── scripts/
```

## Suggested Java package root

`io.grayfile`

## Suggested initial modules

- `grayfile-gateway`
- `grayfile-metering`
- `grayfile-persistence`
- `grayfile-observability`

## Success criteria for V1

- A caller can send a chat completion request to GrayFile
- GrayFile forwards the request to the configured backend
- GrayFile returns the backend response unchanged except for optional gateway-added headers
- GrayFile persists a usage event using the backend `usage` object
- GrayFile updates and closes billing windows correctly according to the dual-threshold rule
- Gateway metrics are exposed for Prometheus scraping
