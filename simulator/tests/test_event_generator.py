"""Tests for the network event generator."""

import random
import uuid
from datetime import datetime

from simulator.src.device_registry import DeviceProfile, build_device_fleet
from simulator.src.event_generator import (
    AnomalyType,
    generate_anomalous_event,
    generate_normal_event,
)

_REQUIRED_FIELDS = {
    "event_id",
    "timestamp",
    "device_id",
    "src_ip",
    "dst_ip",
    "src_port",
    "dst_port",
    "protocol",
    "bytes_sent",
    "bytes_received",
    "packets",
    "duration_ms",
    "flags",
}

_VALID_PROTOCOLS = {"TCP", "UDP", "ICMP", "DNS", "HTTP", "HTTPS"}


def _sample_device() -> DeviceProfile:
    return build_device_fleet(5, seed=1)[0]


# ---- Normal events ----


def test_normal_event_has_all_fields():
    event = generate_normal_event(_sample_device(), rng=random.Random(0))
    assert _REQUIRED_FIELDS.issubset(event.keys())


def test_normal_event_uuid():
    event = generate_normal_event(_sample_device(), rng=random.Random(0))
    uuid.UUID(event["event_id"])  # raises if invalid


def test_normal_event_timestamp_is_iso():
    event = generate_normal_event(_sample_device(), rng=random.Random(0))
    datetime.fromisoformat(event["timestamp"])


def test_normal_event_protocol_valid():
    rng = random.Random(7)
    for _ in range(200):
        event = generate_normal_event(_sample_device(), rng=rng)
        assert event["protocol"] in _VALID_PROTOCOLS


def test_normal_event_numeric_bounds():
    rng = random.Random(99)
    for _ in range(500):
        event = generate_normal_event(_sample_device(), rng=rng)
        assert event["bytes_sent"] >= 0
        assert event["bytes_received"] >= 0
        assert event["packets"] >= 1
        assert event["duration_ms"] >= 0
        assert 0 <= event["src_port"] <= 65535
        assert 0 <= event["dst_port"] <= 65535


def test_normal_event_uses_device_id_and_ip():
    device = _sample_device()
    event = generate_normal_event(device, rng=random.Random(0))
    assert event["device_id"] == device.device_id
    assert event["src_ip"] == device.ip


def test_normal_event_empty_flags():
    event = generate_normal_event(_sample_device(), rng=random.Random(0))
    assert event["flags"] == []


# ---- Anomalous events ----


def test_anomalous_event_has_all_fields():
    event = generate_anomalous_event(_sample_device(), rng=random.Random(0))
    assert _REQUIRED_FIELDS.issubset(event.keys())


def test_anomalous_event_flags_non_empty():
    rng = random.Random(42)
    for _ in range(100):
        event = generate_anomalous_event(_sample_device(), rng=rng)
        assert len(event["flags"]) > 0, "anomalous events must have at least one flag"


def test_anomalous_event_covers_all_types():
    """Over many draws every anomaly type should appear at least once."""
    seen_flags: set[str] = set()
    rng = random.Random(12)
    for _ in range(500):
        event = generate_anomalous_event(_sample_device(), rng=rng)
        seen_flags.update(event["flags"])

    for at in AnomalyType:
        assert at.value in seen_flags, f"anomaly type {at.value} never generated"


def test_port_scan_characteristics():
    """Port scans should have many packets but very few bytes."""
    rng = random.Random(0)
    port_scans = []
    for _ in range(1000):
        event = generate_anomalous_event(_sample_device(), rng=rng)
        if "port_scan" in event["flags"]:
            port_scans.append(event)

    assert len(port_scans) > 0
    for ps in port_scans:
        assert ps["packets"] >= 80
        assert ps["bytes_sent"] <= 120


def test_data_exfil_characteristics():
    """Exfiltration events should have very large bytes_sent."""
    rng = random.Random(0)
    exfils = []
    for _ in range(1000):
        event = generate_anomalous_event(_sample_device(), rng=rng)
        if "data_exfiltration" in event["flags"]:
            exfils.append(event)

    assert len(exfils) > 0
    for ex in exfils:
        assert ex["bytes_sent"] >= 500_000


# ---- Device fleet ----


def test_fleet_size():
    fleet = build_device_fleet(20)
    assert len(fleet) == 20


def test_fleet_deterministic():
    a = build_device_fleet(10, seed=1)
    b = build_device_fleet(10, seed=1)
    assert [d.device_id for d in a] == [d.device_id for d in b]
    assert [d.ip for d in a] == [d.ip for d in b]


def test_fleet_unique_ids():
    fleet = build_device_fleet(50)
    ids = [d.device_id for d in fleet]
    assert len(ids) == len(set(ids))


def test_fleet_unique_ips():
    fleet = build_device_fleet(50)
    ips = [d.ip for d in fleet]
    assert len(ips) == len(set(ips))
