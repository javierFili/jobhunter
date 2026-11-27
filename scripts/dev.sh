#!/usr/bin/env bash
# Arranca backend (puerto 8095) y frontend (puerto 5175) en desarrollo. Requiere .env en la raíz.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ ! -f "$ROOT/.env" ]]; then
  echo "Falta $ROOT/.env (copia .env.example y completa JOBHUNTER_JWT_SECRET y JOBHUNTER_MASTER_KEY)" >&2
  exit 1
fi
set -a; source "$ROOT/.env"; set +a
(cd "$ROOT/backend" && ./mvnw -q spring-boot:run) &
BACK=$!
(cd "$ROOT/frontend" && npm run dev) &
FRONT=$!
trap 'kill $BACK $FRONT 2>/dev/null' EXIT INT TERM
wait
