#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 -m py_compile tests/smoke.py
node --check web/app.js
docker compose config --quiet
for svc in identity-service content-service assessment-service membership-service interaction-service; do
  docker compose build "$svc"
done
python3 tests/smoke.py
