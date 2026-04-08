#!/usr/bin/env bash
set -euo pipefail

./mvnw -pl gateway -am clean test

docker compose -f deploy/docker-compose.yml up -d --build

echo "GrayFile bootstrap complete."
