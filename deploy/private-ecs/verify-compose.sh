#!/usr/bin/env bash
# Validate without ever displaying rendered configuration or tool diagnostics.
set +x
set -euo pipefail
umask 077
fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || fail docker_available
command -v jq >/dev/null 2>&1 || fail jq_available
ENV_FILE=${1:-/opt/fishbook/config/fishbook.env}
[[ -f "$ENV_FILE" && -r "$ENV_FILE" ]] || fail env_file_readable
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
[[ -f "$ROOT/compose.private-ecs.yaml" ]] || fail compose_file_exists
rendered=$(mktemp) || fail private_temporary_file
trap 'rm -f -- "$rendered"' EXIT
# Shell variables must not override the explicitly supplied secret file.
env -u MYSQL_PASSWORD -u FISHBOOK_ADMIN_BOOTSTRAP_ENABLED -u FISHBOOK_ADMIN_EMAIL \
    -u FISHBOOK_ADMIN_PASSWORD -u FISHBOOK_ADMIN_NICKNAME \
    docker compose --env-file "$ENV_FILE" -f "$ROOT/compose.private-ecs.yaml" \
    config --format json >"$rendered" 2>/dev/null || fail compose_render
check() { jq -e "$2" "$rendered" >/dev/null 2>&1 || fail "$1"; }
check exactly_two_services '(.services | keys) == ["backend", "frontend"]'
check one_backend '(.services.backend.scale // 1) == 1 and (.services.backend.deploy.replicas // 1) == 1'
check loopback_https_only '.services.frontend.ports | length == 1 and .[0].target == 8443 and .[0].published == "8443" and .[0].host_ip == "127.0.0.1" and .[0].protocol == "tcp"'
check no_backend_ports '(.services.backend.ports // []) | length == 0'
check isolated_containers 'all(.services[]; (.privileged // false) == false and (.network_mode // "") != "host" and all(.volumes[]?; ((.source // "") | test("docker\\.sock")) | not))'
check backend_uid '.services.backend.user == "10001:10001"'
check frontend_uid '.services.frontend.user == "101:101"'
check private_photo_mount '.services.backend.volumes | length == 1 and any(.type == "bind" and .source == "/opt/fishbook/data/photos" and .target == "/data/photos" and (.read_only // false) == false and (.bind.create_host_path // false) == false)'
check readonly_tls_mount '.services.frontend.volumes | length == 1 and any(.type == "bind" and .source == "/opt/fishbook/tls" and .target == "/etc/fishbook/tls" and .read_only == true and (.bind.create_host_path // false) == false)'
check filesystem_provider '.services.backend.environment | .FISHBOOK_MEDIA_ENABLED == "true" and .FISHBOOK_MEDIA_PROVIDER == "filesystem" and .FISHBOOK_MEDIA_FILESYSTEM_ROOT == "/data/photos"'
check secure_cookie '.services.backend.environment.SERVER_SERVLET_SESSION_COOKIE_SECURE == "true"'
check internal_rds_learning_endpoint '.services.backend.environment | .SPRING_DATASOURCE_USERNAME == "fishbook_app" and .SPRING_DATASOURCE_URL == "jdbc:mysql://rm-bp1pgdmw41u3i6r98.mysql.rds.aliyuncs.com:3306/fishbook?connectionTimeZone=UTC&useSSL=false&allowPublicKeyRetrieval=true&sessionVariables=explicit_defaults_for_timestamp=ON"'
check restart_health_logs 'all(.services[]; .restart == "unless-stopped" and (.healthcheck.disable // false) == false and (.healthcheck.test | length > 1) and .healthcheck.test[0] != "NONE" and .logging.driver == "json-file" and .logging.options["max-size"] == "10m" and .logging.options["max-file"] == "3")'
printf 'PASS: private_ecs_compose\n'
