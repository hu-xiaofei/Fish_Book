# FishBook Local Development Runbook

## Fresh Setup

Install Temurin JDK 21, nvm, Node.js 24.18.0, and Docker Desktop with Docker Compose. Then clone and start the full application:

```bash
git clone https://github.com/hu-xiaofei/Fish_Book.git
cd Fish_Book
nvm install 24.18.0
nvm use 24.18.0
test -f .env || cp .env.example .env
docker compose -f compose.yaml -f compose.full.yaml up -d --build
```

Keep `.env` local. Do not commit its development credentials. Wait until `docker compose -f compose.yaml -f compose.full.yaml ps` reports MySQL, MinIO, and the backend as healthy, then open `http://localhost:8080`.

## Normal Start and Stop

Start or refresh the complete same-origin stack:

```bash
docker compose -f compose.yaml -f compose.full.yaml up -d --build
```

Stop it while preserving MySQL and MinIO data:

```bash
docker compose -f compose.yaml -f compose.full.yaml down
```

For dependency-only development, start MySQL and MinIO, then run the backend and frontend in separate terminals:

```bash
docker compose up -d
```

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
cd frontend
nvm use 24.18.0
npm ci
npm run dev
```

## Inspect Logs and Health

Check service state and the public health boundary:

```bash
docker compose -f compose.yaml -f compose.full.yaml ps
curl -fsS http://localhost:8080/actuator/health
```

Follow all logs, or narrow the output to one service:

```bash
docker compose -f compose.yaml -f compose.full.yaml logs --tail=200 -f
docker compose -f compose.yaml -f compose.full.yaml logs --tail=200 -f backend
docker compose -f compose.yaml -f compose.full.yaml logs --tail=200 -f mysql
docker compose -f compose.yaml -f compose.full.yaml logs --tail=200 -f minio
```

Press `Ctrl-C` to stop following logs; the containers continue running.

## Catalog Smoke Checks

The public catalog is available at [the catalog home page](http://localhost:8080/), the [乌鳢 detail page](http://localhost:8080/fish/channa-argus), and the [catalog attribution record](../data-sources/fish-catalog-attribution.md). After the full stack is healthy, verify its public API and locally served image with:

```bash
curl -fsS 'http://localhost:8080/api/v1/fish?page=0'
curl -fsS 'http://localhost:8080/api/v1/fish?q=%E9%BB%91%E9%B1%BC'
curl -fsS 'http://localhost:8080/api/v1/fish/channa-argus'
curl -I 'http://localhost:8080/images/fish/channa-argus.jpg'
```

Each API request should return JSON without a login session. The image response should be `200 OK` with an `image/jpeg` content type. Catalog writes and image uploads are intentionally unavailable; the catalog and its local image assets are read-only.

If a catalog endpoint is unavailable after an image rebuild, check the public health endpoint and backend logs first:

```bash
curl -fsS http://localhost:8080/actuator/health/readiness
docker compose -f compose.yaml -f compose.full.yaml logs --tail=300 backend mysql
```

For a migration failure, use the Flyway diagnosis below and confirm V3 and V4 appear once in `flyway_schema_history` before retrying the smoke checks.

## Private Catch-Photo Storage

The full Compose stack enables private media and maps these local settings into the backend:

| `.env` setting | Backend setting | Purpose |
| --- | --- | --- |
| `MINIO_ROOT_USER` | `FISHBOOK_MEDIA_ACCESS_KEY` | Local MinIO access key |
| `MINIO_ROOT_PASSWORD` | `FISHBOOK_MEDIA_SECRET_KEY` | Local MinIO secret key |
| `MINIO_BUCKET` | `FISHBOOK_MEDIA_BUCKET` | Private object bucket |
| — | `FISHBOOK_MEDIA_ENDPOINT=http://minio:9000` | Container-network endpoint |
| — | `FISHBOOK_MEDIA_ENABLED=true` | Enables the MinIO adapter and cleanup worker |

When the backend runs directly with the `local` profile, its default endpoint is `http://localhost:9000`; the MinIO credentials and bucket fall back to the matching `.env` names. Override the `FISHBOOK_MEDIA_*` variables when using another local endpoint. Media is disabled by default outside the local profile so unit and slice tests do not silently depend on object storage.

At startup the backend checks for the configured bucket and creates it if absent. Keep that bucket private: do not add anonymous download policies or expose object URLs to the browser. If media is enabled but MinIO, its credentials, or bucket initialization is unavailable, backend startup fails instead of starting with an unusable media boundary.

Each catch record accepts at most one JPEG, PNG, or WebP photo up to 10 MiB. All endpoints require an authenticated record owner:

| Method and endpoint | Behavior |
| --- | --- |
| `PUT /api/v1/catches/{id}/photo` | Uploads or replaces multipart field `photo`; returns `204` |
| `GET /api/v1/catches/{id}/photo` | Returns private binary content with `Cache-Control: private` |
| `DELETE /api/v1/catches/{id}/photo` | Removes the current photo metadata; returns `204` |

A missing record, absent photo, and another user's photo all return the same `404` photo-not-found response. Invalid type, signature, or size returns `400`; a storage outage during a direct upload or read returns `503`. During record creation, a failed optional upload does not roll back the saved record, and the detail page offers a retry.

## Inspect Media Cleanup Safely

Replacing or removing a photo, or deleting its catch record, commits the database change and a cleanup job in one transaction. The worker processes at most 20 due jobs each minute. Failures use exponential backoff and become `FAILED` after eight attempts; a successful object deletion removes its job row. Cleanup failure never restores a photo to the UI.

Open the MySQL client as described below and inspect job metadata without selecting private object keys:

```sql
SELECT id, reason, status, attempt_count, next_attempt_at, last_attempt_at, created_at
FROM media_cleanup_jobs
ORDER BY id DESC;
```

`PENDING` means deletion will be retried. `FAILED` requires operator investigation of MinIO availability and credentials before a controlled retry or cleanup. Normal backend warnings identify only the job ID, reason, attempt count, and error class; they intentionally omit private object keys.

For an orphan audit, work only in a trusted local terminal. Compare the object listing with both live record references and queued cleanup references:

```sql
SELECT id, user_id, photo_object_key
FROM catch_records
WHERE photo_object_key IS NOT NULL
ORDER BY id;

SELECT id, status, object_key
FROM media_cleanup_jobs
ORDER BY id;
```

```bash
docker compose -f compose.yaml -f compose.full.yaml exec minio \
  sh -c 'MC_HOST_local="http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@localhost:9000" mc ls --recursive local/"$MINIO_BUCKET"'
```

Treat keys referenced by `catch_records` as live and keys referenced by `media_cleanup_jobs` as awaiting or requiring cleanup. An object is an orphan candidate only when it appears in neither query. Recheck both tables immediately before deleting any candidate, retain a backup when recovery matters, and never paste object keys or command output into shared logs, issues, or chat.

## Diagnose Flyway Failures

Start with the backend and MySQL logs:

```bash
docker compose -f compose.yaml -f compose.full.yaml logs --tail=300 backend mysql
```

Open the MySQL client using the username, password, and database from `.env`:

```bash
docker compose -f compose.yaml -f compose.full.yaml exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" "$MYSQL_DATABASE"'
```

At the MySQL prompt, inspect migration history:

```sql
SELECT installed_rank, version, description, script, checksum, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Confirm that the failing migration name and checksum match the files under `backend/src/main/resources/db/migration`. Never edit an already-applied migration to repair a shared database. Add a new forward migration instead. Do not run Flyway repair until the cause and the intended schema state are understood.

## Inspect JDBC Sessions Safely

Open the same MySQL client, then inspect session metadata:

```sql
SELECT PRIMARY_ID, SESSION_ID, PRINCIPAL_NAME,
       FROM_UNIXTIME(CREATION_TIME / 1000) AS created_at,
       FROM_UNIXTIME(LAST_ACCESS_TIME / 1000) AS last_accessed_at,
       FROM_UNIXTIME(EXPIRY_TIME / 1000) AS expires_at
FROM SPRING_SESSION
ORDER BY LAST_ACCESS_TIME DESC;

SELECT SESSION_PRIMARY_ID, ATTRIBUTE_NAME, OCTET_LENGTH(ATTRIBUTE_BYTES) AS stored_bytes
FROM SPRING_SESSION_ATTRIBUTES
ORDER BY SESSION_PRIMARY_ID, ATTRIBUTE_NAME;
```

Inspect attribute names and byte lengths only. Do not print or copy `ATTRIBUTE_BYTES`; it contains serialized security state. Raw passwords must never be stored in either session table.

## Preserve or Reset Local Data

The normal stop command preserves the named `fishbook_mysql-data` and `fishbook_minio-data` volumes:

```bash
docker compose -f compose.yaml -f compose.full.yaml down
```

**Destructive reset:** the following command permanently deletes the local MySQL database, users, sessions, Flyway history, and MinIO objects held in Compose volumes:

```bash
docker compose -f compose.yaml -f compose.full.yaml down -v
```

Use it only when disposable local data is understood and intentionally being discarded. Recreate the stack afterward with `up -d --build`; Flyway will rebuild an empty schema.

## Full Verification Sequence

Copy `.env.example` once if `.env` is absent, start the complete stack without deleting its volumes, and run every layer in this order:

```bash
test -f .env || cp .env.example .env
docker compose -f compose.yaml -f compose.full.yaml up -d --build

cd backend && ./mvnw test
cd ../frontend && npm ci && npm run lint && npm test && npm run build
cd ../e2e && npm ci && npx playwright install chromium && npm test
cd .. && docker compose -f compose.yaml -f compose.full.yaml config --quiet
```

The Playwright acceptance suite proves registration, login, JDBC-backed session restoration after reload, nickname persistence, logout, protected-route redirection, the public catalog, private favorites, catch-record CRUD, and private-photo upload, owner isolation, reload, replacement, and removal.
