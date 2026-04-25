"""NetWatch AI - Anomaly Explainer Service

Consumes anomaly events from Kafka, generates human-readable explanations
using a local Llama 3.2 model via Ollama, then publishes enriched events
to the explained-events topic for the API service to pick up.
"""

import asyncio
import json
import logging

import structlog
from confluent_kafka import Producer

from . import config
from .consumer import AnomalyConsumer
from .ollama_client import OllamaClient

structlog.configure(
    wrapper_class=structlog.make_filtering_bound_logger(logging.INFO),
    processors=[
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.JSONRenderer(),
    ],
)
log = structlog.get_logger()

EXPLAINED_TOPIC = "explained-events"


def _make_producer() -> Producer:
    return Producer({"bootstrap.servers": config.KAFKA_BOOTSTRAP_SERVERS})


def _delivery_report(err, msg) -> None:
    if err:
        log.warning("delivery_failed", topic=msg.topic(), error=str(err))


async def handle_event(
    event: dict,
    ollama: OllamaClient,
    producer: Producer,
) -> None:
    """Generate an explanation and publish the enriched event."""
    event_id = event.get("event_id", "unknown")
    device_id = event.get("device_id", "unknown")

    try:
        explanation = await ollama.explain(event)
        log.info("explained", event_id=event_id, device_id=device_id,
                 chars=len(explanation))
    except Exception as exc:
        log.warning("explain_failed", event_id=event_id, error=str(exc))
        explanation = "Explanation unavailable."

    enriched = {**event, "explanation": explanation}
    producer.produce(
        EXPLAINED_TOPIC,
        key=device_id,
        value=json.dumps(enriched),
        callback=_delivery_report,
    )
    producer.poll(0)  # trigger delivery callbacks without blocking


def main() -> None:
    log.info("starting", topic=config.KAFKA_TOPIC, model=config.OLLAMA_MODEL)

    ollama = OllamaClient(base_url=config.OLLAMA_BASE_URL, model=config.OLLAMA_MODEL)
    producer = _make_producer()
    consumer = AnomalyConsumer(
        bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS,
        topic=config.KAFKA_TOPIC,
        group_id=config.KAFKA_GROUP_ID,
    )

    loop = asyncio.new_event_loop()

    def handler(event: dict) -> None:
        loop.run_until_complete(handle_event(event, ollama, producer))

    try:
        consumer.consume_loop(handler)
    finally:
        producer.flush(timeout=10)
        loop.run_until_complete(ollama.aclose())
        loop.close()
        log.info("shutdown_complete")


if __name__ == "__main__":
    main()
