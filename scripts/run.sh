#!/usr/bin/env bash
# Starts MySQL + Artemis (Docker) and the SmartRent backend. Run from the project root.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "No .env found - copying .env.example. EDIT THE PASSWORDS in .env, then re-run."
  cp .env.example .env
  exit 1
fi

set -a; source .env; set +a

docker compose up -d
echo "Waiting for MySQL to become healthy..."
until [ "$(docker inspect -f '{{.State.Health.Status}}' smartrent-mysql 2>/dev/null)" = "healthy" ]; do sleep 2; done

cd backend
mvn spring-boot:run
