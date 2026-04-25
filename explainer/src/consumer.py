"""Kafka consumer for anomaly events."""

import signal
import json
import logging

from confluent_kafka import Consumer, KafkaError, KafkaException

logger = logging.getLogger(__name__)


class AnomalyConsumer:
    """Consumes events from the anomaly-events Kafka topic."""

    def __init__(self, bootstrap_servers: str, topic: str, group_id: str) -> None:
        self._topic = topic
        self._running = True
        self._consumer = Consumer({
            "bootstrap.servers": bootstrap_servers,
            "group.id": group_id,
            "auto.offset.reset": "earliest",
            "enable.auto.commit": True,
        })
        self._consumer.subscribe([topic])
        logger.info("Subscribed to topic=%s group=%s", topic, group_id)

        # Honour SIGTERM so Docker stop doesn't wait the full timeout.
        signal.signal(signal.SIGTERM, self._handle_sigterm)

    def consume_loop(self, handler) -> None:
        """Poll for messages and invoke handler(event_dict) on each."""
        try:
            while self._running:
                msg = self._consumer.poll(timeout=1.0)
                if msg is None:
                    continue
                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        logger.debug("Reached end of partition %s", msg.partition())
                    else:
                        raise KafkaException(msg.error())
                    continue

                try:
                    event = json.loads(msg.value().decode("utf-8"))
                    handler(event)
                except Exception as exc:
                    logger.warning("Failed to handle message offset=%s: %s",
                                   msg.offset(), exc)
        finally:
            self.close()

    def close(self) -> None:
        self._running = False
        self._consumer.close()
        logger.info("Consumer closed")

    def _handle_sigterm(self, _signum, _frame) -> None:
        logger.info("SIGTERM received — stopping consumer loop")
        self._running = False
