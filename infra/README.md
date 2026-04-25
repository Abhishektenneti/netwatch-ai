# Infra

Infrastructure configuration for NetWatch AI — Docker Compose for local development and Kubernetes manifests for production deployments.

## Local development (Docker Compose)

The `docker-compose.yml` at the project root spins up the full stack with a single command:

```bash
docker compose up --build
```

### Services started

| Service | Port(s) | Description |
|---|---|---|
| Zookeeper | 2181 | Kafka coordination |
| Kafka | 9092, 29092 | Event streaming broker |
| kafka-init | — | Creates all required topics on startup |
| Qdrant | 6333 (HTTP), 6334 (gRPC) | Vector database |
| MLflow | 5000 | Model experiment tracking UI |
| Ollama | 11434 | Local LLM server |
| simulator | — | Network event generator |
| processor | 8081 | Anomaly detection service |
| explainer | — | LLM explanation service |
| api | 8080 | REST API |

### Kafka topics created automatically

| Topic | Partitions | Description |
|---|---|---|
| `raw-events` | 6 | Raw network events from Simulator |
| `processed-events` | 6 | Enriched events with anomaly scores |
| `anomaly-events` | 3 | Flagged anomaly events |
| `explained-events` | 3 | Anomalies with LLM explanations |

### Useful URLs

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Processor stats | http://localhost:8081/api/stats |
| MLflow UI | http://localhost:5000 |
| Qdrant dashboard | http://localhost:6333/dashboard |
| Ollama | http://localhost:11434 |

## Kubernetes (`k8s/`)

Production-ready Kubernetes manifests for deploying to a cluster.

```
k8s/
├── namespace.yml         # netwatch namespace
├── kafka/
│   └── deployment.yml    # Kafka + Zookeeper
├── qdrant/
│   └── deployment.yml    # Qdrant with PersistentVolume (TODO)
├── mlflow/
│   └── deployment.yml    # MLflow with PersistentVolume (TODO)
├── processor/
│   └── deployment.yml    # Processor Deployment + Service
├── api/
│   └── deployment.yml    # API Deployment + Service
├── simulator/
│   └── deployment.yml    # Simulator Deployment
└── explainer/
    └── deployment.yml    # Explainer Deployment
```

### Deploy to a cluster

```bash
# Create the namespace first
kubectl apply -f k8s/namespace.yml

# Deploy infrastructure
kubectl apply -f k8s/kafka/
kubectl apply -f k8s/qdrant/
kubectl apply -f k8s/mlflow/

# Deploy application services
kubectl apply -f k8s/processor/
kubectl apply -f k8s/api/
kubectl apply -f k8s/simulator/
kubectl apply -f k8s/explainer/
```

> **Note:** Persistent volumes for Qdrant and MLflow are not yet configured — data will not survive pod restarts. See the TODO comments in the respective deployment files.
