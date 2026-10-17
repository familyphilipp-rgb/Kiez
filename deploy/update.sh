#!/bin/sh
# Auf dem Server im Kiez-Ordner ausführen: holt den neuesten Stand und startet Kiez neu.
set -e
cd "$(dirname "$0")/.."
git pull --ff-only
docker compose -f deploy/docker-compose.yml up -d --build
docker compose -f deploy/docker-compose.yml ps
