"""Configuration loaded from environment variables."""

import os


KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "raw-events")
EVENT_RATE_PER_SEC = int(os.getenv("EVENT_RATE_PER_SEC", "10"))

# Probability that any given event is anomalous (0.0 - 1.0)
ANOMALY_RATIO = float(os.getenv("ANOMALY_RATIO", "0.05"))

# Number of simulated devices on the network
NUM_DEVICES = int(os.getenv("NUM_DEVICES", "20"))
