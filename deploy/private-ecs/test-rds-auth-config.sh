#!/usr/bin/env bash
# Regression: a non-TLS RDS connection must support RSA password exchange and
# opt into explicit TIMESTAMP defaults for this connection only.
set -euo pipefail
umask 077

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
env_file=$(mktemp)
rendered=$(mktemp)
trap 'rm -f -- "$env_file" "$rendered"' EXIT

printf '%s\n' \
  "MYSQL_PASSWORD='synthetic-db-password'" \
  "FISHBOOK_ADMIN_BOOTSTRAP_ENABLED='true'" \
  "FISHBOOK_ADMIN_EMAIL='admin@example.invalid'" \
  "FISHBOOK_ADMIN_PASSWORD='synthetic-admin-password'" \
  "FISHBOOK_ADMIN_NICKNAME='Synthetic Admin'" >"$env_file"

docker compose --env-file "$env_file" -f "$root/compose.private-ecs.yaml" \
  config --format json >"$rendered"

if ! jq -e '
  .services.backend.environment.SPRING_DATASOURCE_URL ==
  "jdbc:mysql://rm-bp1pgdmw41u3i6r98.mysql.rds.aliyuncs.com:3306/fishbook?connectionTimeZone=UTC&useSSL=false&allowPublicKeyRetrieval=true&sessionVariables=explicit_defaults_for_timestamp=ON"
' "$rendered" >/dev/null; then
  printf 'FAIL: private_rds_connection_settings\n' >&2
  exit 1
fi

bash "$root/deploy/private-ecs/verify-compose.sh" "$env_file"
