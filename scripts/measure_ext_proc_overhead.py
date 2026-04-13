#!/usr/bin/env python3
import argparse
import json
import statistics
import time
import urllib.request


def percentile(sorted_values, pct):
    if not sorted_values:
        return 0.0
    idx = max(0, min(len(sorted_values) - 1, int(round((pct / 100.0) * (len(sorted_values) - 1)))))
    return sorted_values[idx]


def run(url, payload, headers, iterations):
    latencies_ms = []
    for _ in range(iterations):
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        start = time.perf_counter()
        with urllib.request.urlopen(req, timeout=10) as resp:
            resp.read()
        latencies_ms.append((time.perf_counter() - start) * 1000)
    latencies_ms.sort()
    return {
        "count": iterations,
        "p50_ms": round(statistics.median(latencies_ms), 2),
        "p95_ms": round(percentile(latencies_ms, 95), 2),
        "p99_ms": round(percentile(latencies_ms, 99), 2),
    }


def main():
    parser = argparse.ArgumentParser(description="Measure latency p95/p99 overhead with and without ext_proc.")
    parser.add_argument("--baseline-url", required=True, help="URL without ext_proc")
    parser.add_argument("--ext-proc-url", required=True, help="URL with ext_proc")
    parser.add_argument("--iterations", type=int, default=300)
    args = parser.parse_args()

    payload = json.dumps(
        {
            "model": "facebook/opt-125m",
            "messages": [{"role": "user", "content": "ping"}],
        }
    ).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "x-customer-id": "tenant-enterprise",
        "x-api-key-id": "key-1",
    }

    baseline = run(args.baseline_url, payload, headers, args.iterations)
    ext_proc = run(args.ext_proc_url, payload, headers, args.iterations)

    print(json.dumps({
        "baseline": baseline,
        "ext_proc": ext_proc,
        "overhead": {
            "p95_ms": round(ext_proc["p95_ms"] - baseline["p95_ms"], 2),
            "p99_ms": round(ext_proc["p99_ms"] - baseline["p99_ms"], 2),
        },
    }, indent=2))


if __name__ == "__main__":
    main()
