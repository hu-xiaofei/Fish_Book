# FishBook Local Development Runbook

## Fresh Setup

Install Temurin JDK 21, nvm, Node.js 24.18.0, and Docker Desktop with Docker Compose. Then clone and start the full application:

```bash
git clone https://github.com/hu-xiaofei/Fish_Book.git
cd Fish_Book
nvm install 24.18.0
nvm use 24.18.0
cp .env.example .env
docker compose -f compose.yaml -f compose.full.yaml up -d --build
```

Keep `.env` local. Do not commit its development credentials. Wait until `docker compose -f compose.yaml -f compose.full.yaml ps` reports MySQL and the backend as healthy, then open `http://localhost:8080`.

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

The Playwright acceptance test uses the UI only. It proves registration, login, JDBC-backed session restoration after reload, nickname persistence, logout, and protected-route redirection.
