#!/usr/bin/env bash
# Runs the automated test suite (H2 + embedded Artemis; no Docker needed).
set -euo pipefail
cd "$(dirname "$0")/../backend"
mvn clean verify
