#!/usr/bin/env bash
# Check image metadata without displaying it or passing secret values in arguments.
set +x
set -euo pipefail
umask 077
fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || fail docker_available
command -v jq >/dev/null 2>&1 || fail jq_available
ENV_FILE=${1:-/opt/fishbook/config/fishbook.env}
[[ -f "$ENV_FILE" && -r "$ENV_FILE" && ! -L "$ENV_FILE" ]] || fail env_file_readable
work=$(mktemp -d "${TMPDIR:-/tmp}/fishbook-image-check.XXXXXX" 2>/dev/null) || fail private_temporary_directory
cleanup() {
  result=$?
  trap - EXIT
  rm -f -- "$work/secrets.json" "$work/inspect.json" "$work/history.json" 2>/dev/null || result=1
  rmdir -- "$work" 2>/dev/null || result=1
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP
# Accept only the literal single-quoted password syntax written by the runbook.
# No shell evaluation, environment exports, --arg secrets, or secret-bearing grep patterns.
jq -Rs '
  split("\n") | map(select(test("^(MYSQL_PASSWORD|FISHBOOK_ADMIN_PASSWORD)=")))
  | map(capture("^(?<name>[^=]+)=(?<value>.*)$"))
  | if (map(.name) | sort) != ["FISHBOOK_ADMIN_PASSWORD", "MYSQL_PASSWORD"]
    then error("password_fields") else . end
  | map(.value | if startswith("\u0027") and endswith("\u0027") and length > 2
      then .[1:-1] else error("literal_password") end
    | if test("[\u0027\\\\\r\n]") then error("literal_password") else . end)
' "$ENV_FILE" >"$work/secrets.json" 2>/dev/null || fail literal_password_fields

for service in backend frontend; do
  docker image inspect "fishbook-private-ecs-$service:latest" >"$work/inspect.json" 2>/dev/null \
    || fail "${service}_image_inspect"
  jq -e -s --slurpfile secrets "$work/secrets.json" '
    length == 1 and (.[0] | type == "array" and length == 1)
    and (.[0][0] | .Id | type == "string" and test("^sha256:[0-9a-f]{64}$"))
    and (.[0][0].Config | type == "object")
    and all(.. | strings, (objects | keys[]); . as $value |
      all($secrets[0][]; . as $secret | ($value | contains($secret)) | not))
  ' "$work/inspect.json" >/dev/null 2>&1 || fail "${service}_image_config_no_secrets"
  image_id=$(jq -r '.[0].Id' "$work/inspect.json" 2>/dev/null) || fail "${service}_image_identity"
  # Read history for the immutable ID whose config was just checked.
  docker image history --no-trunc --format '{{json .}}' "$image_id" >"$work/history.json" 2>/dev/null \
    || fail "${service}_image_history"
  jq -e -s --slurpfile secrets "$work/secrets.json" '
    length > 0 and all(.[]; type == "object" and (.CreatedBy | type == "string"))
    and all(.. | strings, (objects | keys[]); . as $value |
      all($secrets[0][]; . as $secret | ($value | contains($secret)) | not))
  ' "$work/history.json" >/dev/null 2>&1 || fail "${service}_image_history_no_secrets"
done
printf 'PASS: private_ecs_images\n'
