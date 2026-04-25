# Simulator

A Python service that generates synthetic network events and publishes them to Kafka, simulating real-world firewall and IDS sensor data.

## What it does

- Maintains a registry of **20 virtual devices**, each with its own behavioral profile (typical byte ranges, packet rates, protocols)
- Continuously generates network events at a configurable rate (default: **10 events/sec**)
- Injects **anomalous events** at a configurable ratio (default: **5%**), drawn from 5 attack patterns:
  - `port_scan` — rapid connections across many destination ports
  - `data_exfiltration` — high outbound byte volumes to external IPs
  - `dns_tunnel` — unusually high DNS packet rates
  - `brute_force` — repeated failed connection attempts
  - `beacon` — periodic low-volume callbacks to a C2 host
- Publishes events as JSON to the `raw-events` Kafka topic

## Configuration

All config is via environment variables:

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka broker address |
| `KAFKA_TOPIC` | `raw-events` | Topic to publish events to |
| `EVENT_RATE_PER_SEC` | `10` | Events generated per second |
| `ANOMALY_RATIO` | `0.05` | Fraction of events that are anomalous (0.0–1.0) |
| `NUM_DEVICES` | `20` | Number of simulated devices |

## Running locally

```bash
cd simulator

# Create and activate virtual environment
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Start (requires Kafka running on localhost:29092)
python src/main.py
```

## Running with Docker

```bash
docker build -t netwatch-simulator .
docker run --env KAFKA_BOOTSTRAP_SERVERS=kafka:9092 netwatch-simulator
```

Or via Docker Compose from the project root:

```bash
docker compose up simulator
```

## Running tests

```bash
cd simulator
source .venv/bin/activate
pytest tests/
```

## Event schema

Events conform to [`shared/schemas/network_event.schema.json`](../shared/schemas/network_event.schema.json).

Example event:
```json
{
  "event_id": "a3f1c2d4-...",
  "timestamp": "2026-04-25T10:00:00Z",
  "device_id": "device-07",
  "src_ip": "192.168.1.107",
  "dst_ip": "203.0.113.42",
  "protocol": "TCP",
  "bytes_sent": 1024,
  "bytes_received": 512,
  "packets": 8,
  "duration_ms": 120,
  "flags": "SYN,ACK"
}
```

## Dependencies

| Package | Version | Purpose |
|---|---|---|
| `confluent-kafka` | 2.3.0 | Kafka producer |
| `faker` | 24.4.0 | Realistic IP and hostname generation |
| `pydantic` | 2.6.4 | Event validation |
| `structlog` | 24.1.0 | Structured logging |
