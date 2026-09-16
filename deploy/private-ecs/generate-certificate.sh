#!/usr/bin/env bash
set +x
set -euo pipefail
umask 077
fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
[[ -n ${FISHBOOK_TLS_DIR:-} ]] || fail explicit_tls_directory_required
[[ "$FISHBOOK_TLS_DIR" = /* && -d "$FISHBOOK_TLS_DIR" && -w "$FISHBOOK_TLS_DIR" && ! -L "$FISHBOOK_TLS_DIR" ]] || fail existing_writable_tls_directory_required
for name in server.crt server.key; do
    [[ ! -e "$FISHBOOK_TLS_DIR/$name" && ! -L "$FISHBOOK_TLS_DIR/$name" ]] || fail refuse_existing_tls_file
done
command -v openssl >/dev/null 2>&1 || fail openssl_available
scratch=$(mktemp -d "$FISHBOOK_TLS_DIR/.certificate.XXXXXXXX") || fail temporary_directory
trap 'rm -f -- "$scratch/server.key" "$scratch/server.crt"; rmdir -- "$scratch"' EXIT
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 397 \
    -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
    -keyout "$scratch/server.key" -out "$scratch/server.crt" >/dev/null 2>&1 || fail certificate_generation
chmod 0644 "$scratch/server.crt"
chmod 0600 "$scratch/server.key"
# Hard links publish without replacing an existing file, even in a race.
ln "$scratch/server.key" "$FISHBOOK_TLS_DIR/server.key" 2>/dev/null || fail refuse_existing_tls_file
ln "$scratch/server.crt" "$FISHBOOK_TLS_DIR/server.crt" 2>/dev/null || fail refuse_existing_tls_file
openssl x509 -in "$FISHBOOK_TLS_DIR/server.crt" -noout -sha256 -fingerprint -enddate
