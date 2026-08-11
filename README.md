# FishBook

FishBook is a learning-oriented fish encyclopedia project. The first delivery slice provides a same-origin React and Spring Boot application with registration, session login, profile editing, and logout.

## Prerequisites

- Temurin JDK 21
- Node.js 24.18.0 (managed with nvm)
- Docker Desktop with Docker Compose

## First Run

```bash
cp .env.example .env
docker compose -f compose.yaml -f compose.full.yaml up -d --build
```

Wait for the services to become healthy, then open `http://localhost:8080`. This full-stack path serves the SPA and API through one origin.

`.env` contains local-only values and must never be committed. See the [local development runbook](docs/runbooks/local-development.md) for dependency-only development, logs, database inspection, and recovery steps.

## Backend

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Frontend

```bash
cd frontend && npm ci && npm run dev
```

## Full Stack

```bash
test -f .env || cp .env.example .env
docker compose -f compose.yaml -f compose.full.yaml up -d --build
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/
```

The health request should return `UP`, and the root request should return the FishBook HTML shell. Open `http://localhost:8080` after the services become healthy. The frontend serves the SPA and proxies `/api` and `/actuator` to the internal backend service.

Stop the full stack without deleting the local MySQL or MinIO data volumes:

```bash
docker compose -f compose.yaml -f compose.full.yaml down
```

## Tests

```bash
cd backend && ./mvnw test
cd ../frontend && npm ci && npm run lint && npm test && npm run build
cd ../e2e && npm ci && npx playwright install chromium && npm test
cd .. && docker compose -f compose.yaml -f compose.full.yaml config --quiet
```

The Playwright test requires the full stack to be running at `http://localhost:8080`.

## Environment Variables

Copy `.env.example` to `.env` for local development. Keep `.env` local-only and never commit it.

## Troubleshooting

Verify the required tool versions, then restart Docker Desktop if Docker commands are unavailable.

`docker compose down` stops the local dependencies while preserving their data volumes.

For Flyway or session-storage diagnosis, follow the [local development runbook](docs/runbooks/local-development.md).
