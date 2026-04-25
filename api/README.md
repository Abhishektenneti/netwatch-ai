# API

A Java / Spring Boot REST service that exposes network alerts and device insights to external clients.

## What it does

- Consumes anomaly events from the `anomaly-events` and `explained-events` Kafka topics
- Persists alerts for querying
- Provides REST endpoints to retrieve alerts filtered by event ID or device
- Queries Qdrant to find devices with similar network behaviour (vector similarity search)

> **Status:** 🚧 In progress — endpoints are defined, implementation pending.

## Endpoints

### Alerts

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/alerts` | List all alerts (paginated) |
| `GET` | `/api/alerts/{eventId}` | Get a specific alert by event ID |
| `GET` | `/api/alerts/device/{deviceId}` | Get all alerts for a device |

### Devices

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/devices/{deviceId}/stats` | Rolling stats and anomaly score history for a device |
| `GET` | `/api/devices/{deviceId}/similar` | Find devices with similar behaviour via Qdrant |

### Health

| Method | Path | Description |
|---|---|---|
| `GET` | `/actuator/health` | Service health check |

## Configuration

All config is in `src/main/resources/application.yml` and overridable via environment variables:

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka broker |
| `QDRANT_HOST` | `localhost` | Qdrant gRPC host |
| `QDRANT_PORT` | `6334` | Qdrant gRPC port |

Kafka topics consumed:

| Topic | Purpose |
|---|---|
| `anomaly-events` | Raw anomaly alerts from Processor |
| `explained-events` | Alerts enriched with LLM explanations from Explainer |
| `processed-events` | All processed events (for device stats) |

## Running locally

```bash
cd api

# Requires Java 21
./gradlew bootRun
```

The service starts on **port 8080**.

## Running with Docker

```bash
docker build -t netwatch-api .
docker run -p 8080:8080 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e QDRANT_HOST=qdrant \
  netwatch-api
```

Or via Docker Compose from the project root:

```bash
docker compose up api
```

## Dependencies

| Library | Purpose |
|---|---|
| Spring Boot 3.2.5 | Application framework |
| Spring Kafka | Kafka consumer |
| Qdrant gRPC client | Device similarity search |
| Lombok | Boilerplate reduction |
| Jackson | JSON serialisation |
