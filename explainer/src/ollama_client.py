"""Client for calling the local Ollama Llama 3.2 model."""

import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)

_PROMPT_TEMPLATE = """\
You are a network security analyst. A statistical anomaly has been detected \
in network traffic. Explain in 2-3 plain-English sentences what this might \
indicate and why it is suspicious.

Device:        {device_id}
Time:          {timestamp}
Source:        {src_ip}:{src_port} → {dst_ip}:{dst_port}
Protocol:      {protocol}
Bytes sent:    {bytes_sent}
Bytes received:{bytes_received}
Packets:       {packets}
Duration:      {duration_ms} ms
Anomaly score: {anomaly_score:.3f}  (0 = normal, 1 = highly anomalous)
Flags:         {flags}

Explanation:"""


class OllamaClient:
    """Generates human-readable anomaly explanations via Ollama."""

    def __init__(self, base_url: str, model: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.model = model
        self._client = httpx.AsyncClient(
            base_url=self.base_url,
            timeout=httpx.Timeout(60.0),  # LLM can be slow
        )

    async def explain(self, event: dict[str, Any]) -> str:
        """Send anomaly event context to the model and return an explanation."""
        prompt = _PROMPT_TEMPLATE.format(
            device_id=event.get("device_id", "unknown"),
            timestamp=event.get("timestamp", ""),
            src_ip=event.get("src_ip", ""),
            src_port=event.get("src_port", ""),
            dst_ip=event.get("dst_ip", ""),
            dst_port=event.get("dst_port", ""),
            protocol=event.get("protocol", ""),
            bytes_sent=event.get("bytes_sent", 0),
            bytes_received=event.get("bytes_received", 0),
            packets=event.get("packets", 0),
            duration_ms=event.get("duration_ms", 0),
            anomaly_score=float(event.get("anomaly_score", 0.0)),
            flags=", ".join(event.get("flags") or []) or "none",
        )

        response = await self._client.post(
            "/api/generate",
            json={
                "model": self.model,
                "prompt": prompt,
                "stream": False,
            },
        )
        response.raise_for_status()
        data = response.json()
        return data.get("response", "").strip()

    async def aclose(self) -> None:
        await self._client.aclose()
