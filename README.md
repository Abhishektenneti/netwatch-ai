# NetWatch AI

NetWatch AI is an event-driven network anomaly detection prototype. It generates synthetic network telemetry, scores each device with an Isolation Forest model, enriches suspicious events with a local language model, and exposes alerts through a REST API.

## What it demonstrates

- Streaming network events through Apache Kafka
- Per-device anomaly detection with a pure-Java Isolation Forest
- Rolling behavioral statistics and device embeddings
- Similar-device search with Qdrant
- Model experiment tracking with MLflow
- Local anomaly explanations through Ollama
- A multi-service development stack with Docker Compose

## Architecture

~~~text
Simulator ──► raw-events ──► Processor ──► processed-events ──► API
                                  │                              │
                                  ├──► anomaly-events ───────────┤
                                  │             │                │
                                  │             ▼                │
                                  │         Explainer            │
                                  │             │                │
                                  └──► Qdrant    └──► explained-events
~~~

| Component | Technology | Responsibility |
| --- | --- | --- |
| [Simulator](./simulator) | Python | Produces realistic normal and anomalous network events |
| [Processor](./processor) | Java, Spring Boot | Maintains rolling windows, trains Isolation Forest models, and scores events |
| [Explainer](./explainer) | Python, Ollama | Turns anomaly details into human-readable explanations |
| [API](./api) | Java, Spring Boot | Serves alerts, device statistics, and similarity results |
| [Shared](./shared) | JSON Schema | Defines the event contracts between services |
| [Infrastructure](./infra) | Docker Compose, Kubernetes | Runs the local stack and provides deployment manifests |

## Quick start

### Prerequisites

- Docker with Docker Compose
- Enough memory to run Kafka, Qdrant, MLflow, Ollama, and the application services

Start the infrastructure, download the local language model into the Ollama container, and launch the complete stack:

~~~bash
docker compose up -d zookeeper kafka kafka-init qdrant mlflow ollama
docker compose exec ollama ollama pull llama3.2
docker compose up --build
~~~

The simulator begins publishing events automatically. The processor needs enough observations for a device before its first model can be trained.

### Local endpoints

| Service | URL |
| --- | --- |
| API health | http://localhost:8080/health |
| Alerts | http://localhost:8080/api/v1/alerts |
| Processor statistics | http://localhost:8081/stats |
| MLflow | http://localhost:5000 |
| Qdrant dashboard | http://localhost:6333/dashboard |
| Ollama | http://localhost:11434 |

Example:

~~~bash
curl http://localhost:8080/health
curl "http://localhost:8080/api/v1/alerts?page=0&limit=20"
~~~

Stop the stack with:

~~~bash
docker compose down
~~~

Add `-v` only when you also want to delete the local Qdrant, MLflow, and Ollama volumes.

## Event flow

| Kafka topic | Producer | Consumer |
| --- | --- | --- |
| `raw-events` | Simulator | Processor |
| `processed-events` | Processor | API |
| `anomaly-events` | Processor | API and Explainer |
| `explained-events` | Explainer | API |

The Processor trains an Isolation Forest per device using a rolling window. Events above the configured score threshold are published as anomalies. The Explainer sends those anomaly features to a local Ollama model and publishes the resulting explanation. The API keeps a bounded in-memory alert store and updates alerts when explanations arrive.

## Configuration

The main settings can be overridden with environment variables in [`docker-compose.yml`](./docker-compose.yml).

| Variable | Default | Used by |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` outside Docker | Simulator, Processor, API, Explainer |
| `EVENT_RATE_PER_SEC` | `10` | Simulator |
| `ANOMALY_RATIO` | `0.05` | Simulator |
| `QDRANT_HOST` | `localhost` outside Docker | Processor, API |
| `QDRANT_PORT` | `6334` | Processor, API |
| `MLFLOW_TRACKING_URI` | `http://localhost:5000` | Processor |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Explainer |
| `OLLAMA_MODEL` | `llama3.2` | Explainer |

Anomaly model parameters live in [`processor/src/main/resources/application.yml`](./processor/src/main/resources/application.yml).

## Run tests

The services can be tested independently:

~~~bash
cd simulator
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pytest tests
~~~

~~~bash
cd processor
./gradlew test
~~~

~~~bash
cd api
./gradlew test
~~~

~~~bash
cd explainer
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pytest tests
~~~

## Project status

The core simulator, processor, API, and explainer implementations are present. This is still a development prototype:

- Alerts are stored in memory and disappear when the API restarts.
- The included traffic is synthetic, not captured from a production network.
- The Kubernetes manifests need production hardening, secrets management, ingress, and persistent storage.
- Full-stack integration and load testing remain useful next steps.
