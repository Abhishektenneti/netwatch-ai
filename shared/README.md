# Shared

Shared JSON Schema definitions for the events that flow between NetWatch AI services.

## Schemas

### `network_event.schema.json`
The raw event produced by the Simulator and consumed by the Processor.

| Field | Type | Required | Description |
|---|---|---|---|
| `event_id` | UUID string | ✅ | Unique event identifier |
| `timestamp` | ISO 8601 datetime | ✅ | Event time |
| `device_id` | string | ✅ | Source device identifier |
| `src_ip` | IPv4 string | ✅ | Source IP address |
| `dst_ip` | IPv4 string | ✅ | Destination IP address |
| `src_port` | integer 0–65535 | — | Source port |
| `dst_port` | integer 0–65535 | — | Destination port |
| `protocol` | enum | ✅ | One of: `TCP`, `UDP`, `ICMP`, `DNS`, `HTTP`, `HTTPS` |
| `bytes_sent` | integer ≥ 0 | ✅ | Bytes sent |
| `bytes_received` | integer ≥ 0 | ✅ | Bytes received |
| `packets` | integer ≥ 1 | ✅ | Packet count |
| `duration_ms` | integer ≥ 0 | ✅ | Connection duration in milliseconds |
| `flags` | string[] | — | TCP flags (e.g. `["SYN", "ACK"]`) |

---

### `processed_event.schema.json`
The enriched event produced by the Processor after anomaly detection. Extends `NetworkEvent` with scoring and rolling statistics. The `explanation` field is populated by the Explainer service.

| Field | Type | Required | Description |
|---|---|---|---|
| `event_id` | UUID string | ✅ | Matches the original `network_event.event_id` |
| `timestamp` | ISO 8601 datetime | ✅ | Event time |
| `device_id` | string | ✅ | Source device |
| `anomaly_score` | number | ✅ | Isolation Forest score in [0, 1] — higher = more anomalous |
| `is_anomaly` | boolean | ✅ | `true` if `anomaly_score ≥ 0.6` |
| `rolling_stats` | object | — | Per-device rolling statistics over the last 200 events |
| `rolling_stats.mean_bytes_sent` | number | — | Mean outbound bytes |
| `rolling_stats.std_bytes_sent` | number | — | Std deviation of outbound bytes |
| `rolling_stats.mean_bytes_received` | number | — | Mean inbound bytes |
| `rolling_stats.std_bytes_received` | number | — | Std deviation of inbound bytes |
| `rolling_stats.mean_duration_ms` | number | — | Mean connection duration |
| `rolling_stats.event_count` | integer | — | Number of events in the rolling window |
| `explanation` | string | — | Natural language explanation from Ollama (added by Explainer) |

## Usage

These schemas are used for:
- **Validation** in the Simulator (pydantic models) and Processor (Jackson)
- **Documentation** — the canonical reference for the event contract between services
- **Testing** — test fixtures in each service are validated against these schemas
