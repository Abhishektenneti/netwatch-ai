"""Pre-generates a fixed fleet of network devices with stable behavioral profiles.

Each device has a deterministic IP, subnet, and a "normal" traffic profile that the
event generator samples from.  This makes the simulated data realistic — a security
camera sends small, frequent UDP packets while a developer workstation sends bursty
HTTPS traffic — and gives the downstream anomaly detector meaningful baselines to
learn.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field


@dataclass(frozen=True)
class DeviceProfile:
    device_id: str
    ip: str
    device_type: str

    # Baseline traffic shape (mean values; generator adds jitter)
    protocols: list[str] = field(default_factory=list)
    dst_port_range: tuple[int, int] = (80, 443)
    bytes_sent_mean: int = 2_000
    bytes_sent_std: int = 500
    bytes_received_mean: int = 5_000
    bytes_received_std: int = 1_500
    packets_mean: int = 15
    packets_std: int = 5
    duration_ms_mean: int = 200
    duration_ms_std: int = 80


#: Archetypes used to build the fleet.  Each entry defines the typical traffic
#: envelope for a class of device so the simulator produces heterogeneous but
#: internally consistent streams.
_ARCHETYPES: list[dict] = [
    {
        "device_type": "workstation",
        "protocols": ["TCP", "HTTP", "HTTPS", "DNS"],
        "dst_port_range": (80, 443),
        "bytes_sent_mean": 4_000,
        "bytes_sent_std": 2_000,
        "bytes_received_mean": 15_000,
        "bytes_received_std": 8_000,
        "packets_mean": 30,
        "packets_std": 15,
        "duration_ms_mean": 350,
        "duration_ms_std": 150,
    },
    {
        "device_type": "server",
        "protocols": ["TCP", "HTTP", "HTTPS"],
        "dst_port_range": (1024, 65535),
        "bytes_sent_mean": 25_000,
        "bytes_sent_std": 12_000,
        "bytes_received_mean": 8_000,
        "bytes_received_std": 4_000,
        "packets_mean": 80,
        "packets_std": 30,
        "duration_ms_mean": 120,
        "duration_ms_std": 60,
    },
    {
        "device_type": "iot_sensor",
        "protocols": ["UDP", "TCP"],
        "dst_port_range": (5000, 5100),
        "bytes_sent_mean": 300,
        "bytes_sent_std": 80,
        "bytes_received_mean": 100,
        "bytes_received_std": 30,
        "packets_mean": 3,
        "packets_std": 1,
        "duration_ms_mean": 50,
        "duration_ms_std": 20,
    },
    {
        "device_type": "security_camera",
        "protocols": ["UDP", "TCP"],
        "dst_port_range": (554, 8554),
        "bytes_sent_mean": 50_000,
        "bytes_sent_std": 10_000,
        "bytes_received_mean": 500,
        "bytes_received_std": 100,
        "packets_mean": 120,
        "packets_std": 30,
        "duration_ms_mean": 1_000,
        "duration_ms_std": 200,
    },
    {
        "device_type": "printer",
        "protocols": ["TCP", "HTTP"],
        "dst_port_range": (9100, 9100),
        "bytes_sent_mean": 800,
        "bytes_sent_std": 300,
        "bytes_received_mean": 15_000,
        "bytes_received_std": 8_000,
        "packets_mean": 10,
        "packets_std": 5,
        "duration_ms_mean": 500,
        "duration_ms_std": 200,
    },
]


def build_device_fleet(num_devices: int, seed: int = 42) -> list[DeviceProfile]:
    """Return a deterministic list of *num_devices* devices.

    Devices are spread across the 10.0.{0..9}.x subnets with IPs assigned
    sequentially so that repeated runs produce the same fleet.
    """
    rng = random.Random(seed)
    devices: list[DeviceProfile] = []

    for i in range(num_devices):
        archetype = _ARCHETYPES[i % len(_ARCHETYPES)]
        subnet = i // 25          # up to 10 subnets of /24
        host = (i % 25) + 10      # .10 … .34 range
        ip = f"10.0.{subnet}.{host}"
        device_id = f"dev-{i:04d}"

        # Add per-device jitter to the archetype means so devices of the same
        # type aren't carbon copies.
        jitter = lambda mean, spread=0.15: max(1, int(mean * rng.uniform(1 - spread, 1 + spread)))

        devices.append(
            DeviceProfile(
                device_id=device_id,
                ip=ip,
                device_type=archetype["device_type"],
                protocols=list(archetype["protocols"]),
                dst_port_range=archetype["dst_port_range"],
                bytes_sent_mean=jitter(archetype["bytes_sent_mean"]),
                bytes_sent_std=jitter(archetype["bytes_sent_std"]),
                bytes_received_mean=jitter(archetype["bytes_received_mean"]),
                bytes_received_std=jitter(archetype["bytes_received_std"]),
                packets_mean=jitter(archetype["packets_mean"]),
                packets_std=jitter(archetype["packets_std"]),
                duration_ms_mean=jitter(archetype["duration_ms_mean"]),
                duration_ms_std=jitter(archetype["duration_ms_std"]),
            )
        )

    return devices
