"""Tests for the Ollama explainer client."""

import json
import pytest
import respx
import httpx

from src.ollama_client import OllamaClient, _PROMPT_TEMPLATE


SAMPLE_EVENT = {
    "event_id": "evt-001",
    "device_id": "dev-A",
    "timestamp": "2025-01-01T00:00:00Z",
    "src_ip": "10.0.0.1",
    "src_port": 12345,
    "dst_ip": "192.168.1.1",
    "dst_port": 443,
    "protocol": "HTTPS",
    "bytes_sent": 5_000_000,
    "bytes_received": 0,
    "packets": 500,
    "duration_ms": 5,
    "anomaly_score": 0.92,
    "flags": ["data_exfiltration"],
}


def test_explain_builds_prompt():
    """Prompt rendered from a known event must contain key identifiers."""
    prompt = _PROMPT_TEMPLATE.format(
        device_id=SAMPLE_EVENT["device_id"],
        timestamp=SAMPLE_EVENT["timestamp"],
        src_ip=SAMPLE_EVENT["src_ip"],
        src_port=SAMPLE_EVENT["src_port"],
        dst_ip=SAMPLE_EVENT["dst_ip"],
        dst_port=SAMPLE_EVENT["dst_port"],
        protocol=SAMPLE_EVENT["protocol"],
        bytes_sent=SAMPLE_EVENT["bytes_sent"],
        bytes_received=SAMPLE_EVENT["bytes_received"],
        packets=SAMPLE_EVENT["packets"],
        duration_ms=SAMPLE_EVENT["duration_ms"],
        anomaly_score=SAMPLE_EVENT["anomaly_score"],
        flags="data_exfiltration",
    )

    assert "dev-A" in prompt
    assert "0.920" in prompt          # anomaly score formatted to 3dp
    assert "10.0.0.1" in prompt
    assert "data_exfiltration" in prompt
    assert "HTTPS" in prompt


@pytest.mark.asyncio
async def test_explain_returns_text():
    """OllamaClient.explain() returns the 'response' field from Ollama's JSON."""
    expected_text = "Large outbound transfer with no inbound bytes is suspicious."

    with respx.mock:
        respx.post("http://localhost:11434/api/generate").mock(
            return_value=httpx.Response(
                200,
                json={"model": "llama3.2", "response": expected_text, "done": True},
            )
        )

        client = OllamaClient(base_url="http://localhost:11434", model="llama3.2")
        result = await client.explain(SAMPLE_EVENT)
        await client.aclose()

    assert result == expected_text


@pytest.mark.asyncio
async def test_explain_handles_missing_flags():
    """Events without a flags field should not raise — flags default to 'none'."""
    event = {**SAMPLE_EVENT, "flags": None}
    expected = "Something unusual happened."

    with respx.mock:
        respx.post("http://localhost:11434/api/generate").mock(
            return_value=httpx.Response(200, json={"response": expected, "done": True})
        )

        client = OllamaClient(base_url="http://localhost:11434", model="llama3.2")
        result = await client.explain(event)
        await client.aclose()

    assert result == expected


@pytest.mark.asyncio
async def test_explain_raises_on_server_error():
    """Non-2xx Ollama responses should propagate as an httpx error."""
    with respx.mock:
        respx.post("http://localhost:11434/api/generate").mock(
            return_value=httpx.Response(500, text="Internal Server Error")
        )

        client = OllamaClient(base_url="http://localhost:11434", model="llama3.2")
        with pytest.raises(httpx.HTTPStatusError):
            await client.explain(SAMPLE_EVENT)
        await client.aclose()
