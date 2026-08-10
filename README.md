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
docker compose up -d
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

`docker compose down` stops the local dependencies while preserving their data. To permanently delete the local MySQL and MinIO volumes, run the destructive command below:

```bash
docker compose down -v
```
