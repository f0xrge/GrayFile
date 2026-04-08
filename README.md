# GrayFile

**Meter. Trace. Close.**

GrayFile is an auditable billing and metering gateway for OpenAI-compatible LLM inference backends such as vLLM and NVIDIA NIM.

It sits between clients and inference services, proxies requests, captures usage information, and applies billing window rules to produce auditable usage records.

## Core capabilities

- OpenAI-compatible request proxying
- Token usage capture from backend responses
- Billing windows based on dual thresholds
- Auditable usage event persistence
- Prometheus-friendly observability
- Backend-agnostic integration model

## Billing model

GrayFile closes a billing window when the first of the following thresholds is reached:

- 10 minutes elapsed
- 1000 tokens accumulated

A new billing window starts immediately after closure.

If a single response crosses the token threshold, overflow tokens are carried into the next billing window.

## Initial target backends

- vLLM
- NVIDIA NIM
- Other OpenAI-compatible inference APIs

## Technology

- Quarkus
- PostgreSQL
- Prometheus
- Grafana
- Docker Compose

## Design principles

- Audit-first
- Backend-agnostic
- Billing-safe
- Operationally observable
- Minimal coupling

## Project status

Early development / proof of concept.
