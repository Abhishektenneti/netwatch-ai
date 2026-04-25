# Processor

A Java / Spring Boot service that consumes raw network events from Kafka, scores them for anomalies using a per-device Isolation Forest model, and publishes enriched results back to Kafka.

## What it does

1. **Consumes** raw events from the `raw-events` Kafka topic
2. **Enriches** each event with rolling statistics (mean, std deviation of byte counts and duration) computed over a sliding window of the last 200 events per device
3. **Trains** an Isolation Forest model asynchronously for each device once its rolling window reaches 256 samples
4. **Scores** each event — events with a score ≥ 0.6 are flagged as anomalies
5. **Publishes** enriched events to `processed-events` and flagged anomalies to `anomaly-events`
6. **Logs** training metrics (anomaly score distribution, tree count, sample size) to MLflow
7. **Stores** device behaviour vectors in Qdrant for similarity queries

## Anomaly detection

The Isolation Forest is a pure-Java implementation of [Liu, Ting & Zhou (2008)](https://cs.nju.edu.cn/zhouzh/zhouzh.files/publication/icdm08b.pdf):
- Each device gets its own ensemble of **100 random binary trees**
- Trees are trained on a subsample of **256 events**
- Path length through the forest is normalised to an anomaly score in **[0, 1]**
- The random seed is derived from `device_id` for **deterministic reproducibility**

## Configuration

All config is in `src/main/resources/application.yml` and overridable via environment variables:

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka broker |
| `QDRANT_HOST` | `localhost` | Qdrant gRPC host |
| `QDRANT_PORT` | `6334` | Qdrant gRPC port |
| `MLFLOW_TRACKING_URI` | `http://localhost:5000` | MLflow server |

Key tuning parameters (set in `application.yml`):

| Parameter | Default | Description |
|---|---|---|
| `netwatch.anomaly.num-trees` | `100` | Isolation Forest tree count |
| `netwatch.anomaly.sample-size` | `256` | Training sample size per device |
| `netwatch.anomaly.score-threshold` | `0.6` | Anomaly flag threshold |
| `netwatch.anomaly.window-size` | `200` | Rolling window size per device |

## Running locally

```bash
cd processor

# Requires Java 21
./gradlew bootRun
```

The service starts on **port 8081**.

## Running with Docker

```bash
docker build -t netwatch-processor .
docker run -p 8081:8081 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e QDRANT_HOST=qdrant \
  -e MLFLOW_TRACKING_URI=http://mlflow:5000 \
  netwatch-processor
```

Or via Docker Compose from the project root:

```bash
docker compose up processor
```

## Running tests

```bash
cd processor
./gradlew test
```

Tests cover:
- `IsolationForestTest` — scoring distribution and edge cases
- `AnomalyDetectionServiceTest` — end-to-end event processing
- `RollingWindowStoreTest` — circular buffer correctness

## Health & metrics

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Service health |
| `GET /actuator/metrics` | Spring metrics |
| `GET /api/stats` | Live anomaly detection stats |

## Dependencies

| Library | Purpose |
|---|---|
| Spring Boot 3.2.5 | Application framework |
| Spring Kafka | Kafka consumer/producer |
| Qdrant gRPC client | Vector storage |
| MLflow Java client | Experiment tracking |
| Lombok | Boilerplate reduction |
| Jackson | JSON serialisation |
