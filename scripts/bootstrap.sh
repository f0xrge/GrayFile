#!/usr/bin/env bash
set -euo pipefail

mvn -pl gateway -am clean test

docker compose -f deploy/docker-compose.yml up -d postgres

echo "GrayFile bootstrap complete."
