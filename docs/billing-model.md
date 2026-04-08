# GrayFile Billing Model

## Rule

GrayFile closes the current billing window when the first of the following thresholds is reached:

- 10 minutes elapsed since window start
- 1000 accumulated tokens

The next billing window starts immediately after closure.

## Definitions

### Usage event
A usage event is produced from a completed inference response and contains at least:
- customer identity
- API key identity
- model
- timestamp
- prompt tokens
- completion tokens
- total tokens
- request ID

### Billing window
A billing window is a bounded period of metered usage for a given billing scope.

Recommended billing scope:
- customer_id
- api_key_id
- model

## Closure reasons

A billing window can be closed for one of these reasons:

- `TIME_LIMIT`
- `TOKEN_LIMIT`

Additional reasons can be added later, such as:
- `MANUAL_CLOSE`
- `ACCOUNT_SUSPENDED`
- `BACKEND_SWITCH`

## Overflow handling

If a single usage event causes the current window to exceed 1000 tokens:

1. The current window is filled up to 1000 tokens
2. It is closed with `TOKEN_LIMIT`
3. Remaining tokens are carried into the next window
4. The process repeats if the overflow spans multiple windows

## Time-based closure

A scheduler should periodically close windows that have exceeded 10 minutes even if no new usage event arrives.

This avoids stale open windows and keeps billing state deterministic.

## Example 1: token threshold reached first

Window start: `14:00:00`

Events:
- `14:03:00` -> 300 tokens
- `14:06:00` -> 500 tokens
- `14:08:00` -> 250 tokens

Result:
- Window 1 closes at `14:08:00`
- Window 1 total = 1000 tokens
- Closure reason = `TOKEN_LIMIT`
- Overflow = 50 tokens moved to Window 2

## Example 2: time threshold reached first

Window start: `14:00:00`

Events:
- `14:04:00` -> 300 tokens
- `14:11:00` -> 100 tokens

Result:
- Window 1 closes at `14:10:00`
- Window 1 total = 300 tokens
- Closure reason = `TIME_LIMIT`
- Window 2 starts at `14:10:00`
- The `14:11:00` event contributes to Window 2

## Persistence recommendations

### usage_events
Store the raw billable evidence.

### billing_windows
Store the aggregated billing windows and closure reason.

### invoice_lines
Can be added later for downstream billing integration.

## Invariants

The implementation should preserve the following invariants:

1. Only one active billing window exists per billing scope
2. Every usage event is persisted exactly once
3. Every billed token belongs to exactly one billing window
4. Billing windows are ordered and non-overlapping
5. Overflow tokens are never lost

## Pricing

GrayFile V1 focuses on metering and billing-window closure, not on final invoice generation.

Pricing can later be layered on top of billing windows using:
- flat fee per window
- proportional cost per token
- model-specific rates
- tenant-specific contracts
