# Explainer

A Python service that consumes anomaly events from Kafka and generates natural language explanations using a local LLM via Ollama.

## What it does

1. **Consumes** anomaly events from the `anomaly-events` Kafka topic
2. **Builds** a structured prompt from the event's features and anomaly score
3. **Calls** Ollama's `/api/generate` endpoint with `llama3.2` to produce a human-readable explanation (e.g. *"This device sent 10x its normal outbound traffic to an external IP in a short window, consistent with data exfiltration"*)
4. **Publishes** the enriched event (with explanation attached) to the `explained-events` Kafka topic

> **Status:** 🚧 In progress — Kafka consumer, Ollama client, and tests are stubbed out.

## Configuration

All config is via environment variables:

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka broker address |
| `KAFKA_TOPIC` | `anomaly-events` | Topic to consume from |
| `KAFKA_GROUP_ID` | `netwatch-explainer` | Kafka consumer group |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2` | Model to use for explanations |

## Running locally

```bash
cd explainer

# Create and activate virtual environment
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Requires Kafka on localhost:29092 and Ollama running with llama3.2
python src/main.py
```

Pull the model before running:

```bash
ollama pull llama3.2
```

## Running with Docker

```bash
docker build -t netwatch-explainer .
docker run \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e OLLAMA_BASE_URL=http://ollama:11434 \
  netwatch-explainer
```

Or via Docker Compose from the project root:

```bash
docker compose up explainer
```

## Running tests

```bash
cd explainer
source .venv/bin/activate
pytest tests/
```

Tests cover (once implemented):
- Prompt construction from anomaly event fields
- Ollama API response parsing
- Graceful handling of Ollama timeouts and errors

## Dependencies

| Package | Version | Purpose |
|---|---|---|
| `confluent-kafka` | 2.3.0 | Kafka consumer |
| `httpx` | 0.27.0 | Async HTTP client for Ollama |
| `pydantic` | 2.6.4 | Event validation |
| `structlog` | 24.1.0 | Structured logging |
| `pytest-asyncio` | 0.23.6 | Async test support |
| `respx` | 0.21.1 | Mock HTTP for tests |
