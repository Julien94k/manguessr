#!/usr/bin/env bash
# Lance le backend en dev avec les variables du .env racine.
#
# Spring Boot ne sait pas lire un fichier .env : il faut donc exporter les variables
# nous-memes. En production, docker-compose s'en charge via `env_file`.
#
# Toute variable deja presente dans l'environnement l'emporte sur le .env, ce qui permet
# de surcharger ponctuellement un port :  SERVER_PORT=8099 ./run-dev.sh
set -euo pipefail

cd "$(dirname "$0")"

# On memorise les surcharges de l'appelant avant de charger le .env.
_preset=$(export -p)

if [ -f ../.env ]; then
  set -a
  # shellcheck disable=SC1091
  source ../.env
  set +a
fi

# On reapplique les surcharges : elles gagnent sur le .env.
eval "$_preset"

# Le conteneur Postgres est publie sur HOST_DB_PORT ; depuis l'hote on passe par localhost.
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-${HOST_DB_PORT:-5432}}"

echo "[run-dev] DB ${DB_HOST}:${DB_PORT}/${DB_NAME:-manguessr} — API sur le port ${SERVER_PORT:-8080}"
exec mvn -B spring-boot:run
