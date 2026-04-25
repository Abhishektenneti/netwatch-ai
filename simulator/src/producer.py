"""Kafka producer wrapper for publishing network events."""

from __future__ import annotations

import json
import logging

from confluent_kafka import Producer, KafkaError

log = logging.getLogger(__name__)


def _delivery_callback(err: KafkaError | None, msg) -> None:
    if err is not None:
        log.error("delivery failed: %s", err)
    else:
        log.debug(
            "delivered to %s [%d] @ %d",
            msg.topic(),
            msg.partition(),
            msg.offset(),
        )


class EventProducer:
    """Publishes network events to Kafka, keyed by device_id."""

    def __init__(self, bootstrap_servers: str, topic: str) -> None:
        self.topic = topic
        self._producer = Producer({
            "bootstrap.servers": bootstrap_servers,
            "linger.ms": 50,
            "batch.num.messages": 500,
            "compression.type": "lz4",
        })

    def send(self, event: dict) -> None:
        """Serialize and send a single event, partitioned by device_id."""
        key = event["device_id"].encode()
        value = json.dumps(event).encode()

        self._producer.produce(
            topic=self.topic,
            key=key,
            value=value,
            callback=_delivery_callback,
        )
        # Trigger any queued delivery callbacks without blocking
        self._producer.poll(0)

    def flush(self, timeout: float = 5.0) -> None:
        """Block until all buffered messages are delivered."""
        self._producer.flush(timeout)

    def close(self) -> None:
        """Flush outstanding messages and release resources."""
        log.info("flushing producer …")
        self._producer.flush(timeout=10.0)
        log.info("producer closed")
