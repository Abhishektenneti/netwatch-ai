# NetWatch AI

An AI-powered network anomaly detection system that ingests real-time network events, detects suspicious behavior using machine learning, and generates human-readable explanations via a local LLM.

## Architecture

```
Simulator ──► raw-events (Kafka) ──► Processor ──► processed-events ──► API
                                          │
                                          └──► anomaly-events (Kafka) ──► Explainer ──► explained-events
```

| Service | Language | Role |
|---|---|---|
| [simulator](./simulator) | Python | Generates synthetic network events and injects them into Kafka |
| [processor](./processor) | Java / Spring Boot | Consumes raw events, runs Isolation Forest, publishes anomaly scores |
| [api](./api) | Java / Spring Boot | REST API for querying alerts and device insights |
| [explainer](./explainer) | Python | Consumes anomaly events and generates natural language explanations via Ollama |
| [infra](./infra) | Docker / Kubernetes | Local dev stack and production manifests |
| [shared](./shared) | JSON Schema | Shared event schema definitions |

## Quickstart

### Prerequisites
- Docker & Docker Compose
- Java 21
- Python 3.10+
- [Ollama](https://ollama.com) with `llama3.2` pulled

### Run locally

```bash
# Pull the LLM model
ollama pull llama3.2

# Start the full stack
docker compose up --build
```

Services will be available at:
- **API** → `http://localhost:8080`
- **Processor** (health/stats) → `http://localhost:8081`
- **MLflow UI** → `http://localhost:5000`
- **Qdrant UI** → `http://localhost:6333/dashboard`
- **Kafka** → `localhost:29092`

### Run services individually

See the README in each service folder for standalone run instructions.

## Kafka Topics

| Topic | Producer | Consumer |
|---|---|---|
| `raw-events` | Simulator | Processor |
| `processed-events` | Processor | API |
| `anomaly-events` | Processor | Explainer, API |
| `explained-events` | Explainer | API |

## Tech Stack

- **Kafka 7.6.0** — event streaming
- **Qdrant 1.8.4** — vector database for device embeddings
- **MLflow 2.12.1** — model experiment tracking
- **Ollama (Llama 3.2)** — local LLM for anomaly explanations
- **Spring Boot 3.2.5** — Java services
- **Python 3.10+** — Python services

## Project Status

| Component | Status |
|---|---|
| Simulator | ✅ Complete |
| Processor (Isolation Forest + MLflow) | ✅ Complete |
| Docker Compose + K8s manifests | ✅ Complete |
| API (AlertController, DeviceController) | 🚧 In progress |
| Explainer (Kafka consumer + Ollama client) | 🚧 In progress |
