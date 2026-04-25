"""Generates fake network events with optional anomaly injection.

Normal events are sampled from each device's behavioral profile (see
device_registry.py).  Anomalous events deliberately violate the profile in one
of several attack-inspired patterns so the downstream Isolation Forest has
meaningful outliers to detect.
"""

from __future__ import annotations

import random
import uuid
from datetime import datetime, timezone
from enum import Enum

from .device_registry import DeviceProfile


class AnomalyType(str, Enum):
    PORT_SCAN = "port_scan"
    DATA_EXFILTRATION = "data_exfiltration"
    DNS_TUNNEL = "dns_tunnel"
    BRUTE_FORCE = "brute_force"
    BEACON = "beacon"


# External IPs used as destinations for normal traffic
_EXTERNAL_SUBNETS = [
    "203.0.113",   # RFC 5737 TEST-NET-3
    "198.51.100",  # RFC 5737 TEST-NET-2
    "192.0.2",     # RFC 5737 TEST-NET-1
]


def _random_external_ip(rng: random.Random) -> str:
    subnet = rng.choice(_EXTERNAL_SUBNETS)
    return f"{subnet}.{rng.randint(1, 254)}"


def _clamp(value: int, lo: int = 0, hi: int | None = None) -> int:
    value = max(lo, value)
    if hi is not None:
        value = min(hi, value)
    return value


def generate_normal_event(
    device: DeviceProfile,
    rng: random.Random | None = None,
) -> dict:
    """Sample a single event from *device*'s normal traffic profile."""
    rng = rng or random.Random()

    protocol = rng.choice(device.protocols)
    dst_port_lo, dst_port_hi = device.dst_port_range
    src_port = rng.randint(1024, 65535)
    dst_port = rng.randint(dst_port_lo, dst_port_hi)

    bytes_sent = _clamp(int(rng.gauss(device.bytes_sent_mean, device.bytes_sent_std)))
    bytes_received = _clamp(int(rng.gauss(device.bytes_received_mean, device.bytes_received_std)))
    packets = _clamp(int(rng.gauss(device.packets_mean, device.packets_std)), lo=1)
    duration_ms = _clamp(int(rng.gauss(device.duration_ms_mean, device.duration_ms_std)))

    return {
        "event_id": str(uuid.uuid4()),
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "device_id": device.device_id,
        "src_ip": device.ip,
        "dst_ip": _random_external_ip(rng),
        "src_port": src_port,
        "dst_port": dst_port,
        "protocol": protocol,
        "bytes_sent": bytes_sent,
        "bytes_received": bytes_received,
        "packets": packets,
        "duration_ms": duration_ms,
        "flags": [],
    }


def generate_anomalous_event(
    device: DeviceProfile,
    rng: random.Random | None = None,
) -> dict:
    """Generate an event that deliberately deviates from the device profile.

    One of several attack patterns is chosen at random:

    - **port_scan**: rapid tiny packets across many high ports
    - **data_exfiltration**: massive outbound transfer to unusual port
    - **dns_tunnel**: DNS protocol with abnormally large payloads
    - **brute_force**: many short TCP connections to auth ports
    - **beacon**: suspiciously regular small packets (C2 pattern)
    """
    rng = rng or random.Random()
    anomaly_type = rng.choice(list(AnomalyType))
    base = generate_normal_event(device, rng)

    if anomaly_type == AnomalyType.PORT_SCAN:
        base["protocol"] = "TCP"
        base["dst_port"] = rng.randint(1, 1023)
        base["bytes_sent"] = rng.randint(40, 120)
        base["bytes_received"] = rng.randint(0, 40)
        base["packets"] = rng.randint(80, 500)
        base["duration_ms"] = rng.randint(5, 50)
        base["flags"] = ["SYN", "port_scan"]

    elif anomaly_type == AnomalyType.DATA_EXFILTRATION:
        base["protocol"] = rng.choice(["TCP", "HTTPS"])
        base["dst_port"] = rng.choice([443, 8443, 4443])
        base["bytes_sent"] = rng.randint(500_000, 5_000_000)
        base["bytes_received"] = rng.randint(100, 1_000)
        base["packets"] = rng.randint(200, 2_000)
        base["duration_ms"] = rng.randint(2_000, 30_000)
        base["flags"] = ["data_exfiltration"]

    elif anomaly_type == AnomalyType.DNS_TUNNEL:
        base["protocol"] = "DNS"
        base["dst_port"] = 53
        base["bytes_sent"] = rng.randint(5_000, 50_000)
        base["bytes_received"] = rng.randint(5_000, 50_000)
        base["packets"] = rng.randint(50, 300)
        base["duration_ms"] = rng.randint(100, 3_000)
        base["flags"] = ["dns_tunnel"]

    elif anomaly_type == AnomalyType.BRUTE_FORCE:
        base["protocol"] = "TCP"
        base["dst_port"] = rng.choice([22, 3389, 445, 3306])
        base["bytes_sent"] = rng.randint(500, 3_000)
        base["bytes_received"] = rng.randint(200, 1_000)
        base["packets"] = rng.randint(50, 200)
        base["duration_ms"] = rng.randint(1_000, 10_000)
        base["flags"] = ["brute_force"]

    elif anomaly_type == AnomalyType.BEACON:
        base["protocol"] = "HTTPS"
        base["dst_port"] = 443
        base["bytes_sent"] = rng.randint(60, 200)
        base["bytes_received"] = rng.randint(60, 200)
        base["packets"] = rng.randint(1, 3)
        base["duration_ms"] = rng.randint(10, 30)
        base["flags"] = ["beacon"]

    return base
