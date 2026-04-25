"""NetWatch AI - Network Event Simulator

Generates fake network events and publishes them to the Kafka 'raw-events' topic,
partitioned by device_id.

Usage:
    python -m src.main          # run with defaults from environment
"""

from __future__ import annotations

import logging
import random
import signal
import time

from . import config
from .device_registry import build_device_fleet
from .event_generator import generate_anomalous_event, generate_normal_event
from .producer import EventProducer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-8s %(name)s  %(message)s",
)
log = logging.getLogger("simulator")

_shutdown = False


def _handle_signal(signum, _frame):
    global _shutdown
    log.info("received signal %s — shutting down", signal.Signals(signum).name)
    _shutdown = True


def main() -> None:
    signal.signal(signal.SIGINT, _handle_signal)
    signal.signal(signal.SIGTERM, _handle_signal)

    log.info(
        "starting simulator  kafka=%s  topic=%s  rate=%d/s  devices=%d  anomaly_ratio=%.2f",
        config.KAFKA_BOOTSTRAP_SERVERS,
        config.KAFKA_TOPIC,
        config.EVENT_RATE_PER_SEC,
        config.NUM_DEVICES,
        config.ANOMALY_RATIO,
    )

    fleet = build_device_fleet(config.NUM_DEVICES)
    producer = EventProducer(config.KAFKA_BOOTSTRAP_SERVERS, config.KAFKA_TOPIC)
    rng = random.Random()

    interval = 1.0 / config.EVENT_RATE_PER_SEC
    total_sent = 0
    anomaly_sent = 0

    try:
        while not _shutdown:
            device = rng.choice(fleet)

            if rng.random() < config.ANOMALY_RATIO:
                event = generate_anomalous_event(device, rng)
                anomaly_sent += 1
            else:
                event = generate_normal_event(device, rng)

            producer.send(event)
            total_sent += 1

            if total_sent % 100 == 0:
                log.info(
                    "sent %d events (%d anomalies, %.1f%%)",
                    total_sent,
                    anomaly_sent,
                    (anomaly_sent / total_sent * 100) if total_sent else 0,
                )

            time.sleep(interval)
    finally:
        producer.close()
        log.info("simulator stopped  total=%d anomalies=%d", total_sent, anomaly_sent)


if __name__ == "__main__":
    main()
