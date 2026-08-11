# FishBook

## Prerequisites

- Temurin JDK 21
- Node.js 24.18.0 (managed with nvm)
- Docker Desktop with Docker Compose

## First Run

```bash
cp .env.example .env
docker compose up -d
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd ../frontend && npm ci && npm run dev
```

`.env` contains local-only values and must never be committed.

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
cp .env.example .env
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
cd frontend && npm test
```

## Environment Variables

Copy `.env.example` to `.env` for local development. Keep `.env` local-only and never commit it.

## Troubleshooting

Verify the required tool versions, then restart Docker Desktop if Docker commands are unavailable.

`docker compose down` stops the local dependencies while preserving their data volumes.
