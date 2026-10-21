# Golden Signals and Metric Catalog — Ticketing & Fulfillment Journey

## Scope

Covers the journey from `01-journey.md`: `booking-service` → `csp-worker` → RabbitMQ → **`ticketing-fulfillment-service`** → PostgreSQL (`ticketing_fulfillment`) → Object storage → SMTP. Primary focus: `ticketing-fulfillment-service`. Names follow Prometheus: `snake_case`, unit suffix, `_total` for counters. Labels bounded; `user_id`, `email`, `reservation_id`, `ticket_id` prohibited as labels (stay in logs per `02-log-contract.md`).

---

## 1. ticketing-fulfillment-service (primary)

### Golden signals

| Signal | Metric | Type | Labels | PromQL |
|---|---|---|---|---|
| Latency | `ticketing_fulfillment_request_duration_seconds` | histogram | `operation`, `outcome` | `histogram_quantile(0.95, sum by (le, operation) (rate(ticketing_fulfillment_request_duration_seconds_bucket{operation=~"inbox_process\|pdf_generate\|email_send\|event_publish"}[5m])))` |
| Traffic | `ticketing_fulfillment_requests_total` | counter | `operation`, `outcome` | `sum(rate(ticketing_fulfillment_requests_total{outcome="success"}[5m])) by (operation)` |
| Errors | `ticketing_fulfillment_requests_total` | counter | `operation`, `outcome` | `sum(rate(ticketing_fulfillment_requests_total{outcome="error"}[5m])) by (operation)` |
| Saturation | `ticketing_fulfillment_inbox_lag` | gauge | `consumer_group` | `max(ticketing_fulfillment_inbox_lag) by (consumer_group)` |
| Saturation | `ticketing_fulfillment_thread_pool_active` | gauge | `pool` | `max(ticketing_fulfillment_thread_pool_active{pool="fulfillment"}) / max(ticketing_fulfillment_thread_pool_max{pool="fulfillment"})` |

### Operations (`operation` values)

`inbox_process` (consume `BookingConfirmed`, deduplicate `eventId`, persist ticket), `pdf_generate` (QR + PDF), `pdf_store` (object storage), `email_send` (SMTP, each retry), `event_publish` (`TicketIssued`).

### Label cardinality

| Label | Cardinality | Bounded? |
|---|---|---|
| `operation` | 5 (fixed) | Yes |
| `outcome` | 3 (`success`,`error`,`retry`) | Yes |
| `consumer_group` | 1–3 | Yes |
| `pool` | 1–2 | Yes |

### Histogram buckets

`[0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0]` — covers p99: inbox <200ms, pdf_gen <1s, pdf_store <2s, email <10s, publish <100ms.

### Extra domain metrics

`ticketing_fulfillment_tickets_issued_total` (counter, `showtime_id`), `ticketing_fulfillment_email_retry_total` (counter, `attempt` 1..3), `ticketing_fulfillment_dlq_events_total` (counter, `reason`).

---

## 2–7. Supporting services (condensed)

**booking-service**: `booking_reservation_confirm_duration_seconds` (hist, `outcome`), `booking_reservations_confirmed_total` (ctr, `outcome`), `booking_outbox_write_failures_total` (ctr, `reason`), `booking_outbox_pending_count` (gauge).

**csp-worker**: `csp_worker_publish_duration_seconds` (hist, `event_type`,`outcome`), `csp_worker_events_published_total` (ctr, `event_type`,`outcome`), `csp_worker_publish_failures_total` (ctr, `error_code`), `csp_worker_outbox_poll_lag_seconds` (gauge).

**RabbitMQ** (exporter): `rabbitmq_message_latency_seconds` (hist, `queue`,`direction`), `rabbitmq_messages_total` (ctr, `queue`,`direction`), `rabbitmq_channel_errors_total` (ctr, `reason`), `rabbitmq_queue_messages_ready` (gauge, `queue`).

**PostgreSQL**: `pg_query_duration_seconds` (hist, `queryid`), `pg_xact_commit_total`/`rollback_total` (ctr), `pg_connections_active` (gauge) / `max_connections`.

**Object storage**: `object_storage_put_duration_seconds` (hist, `bucket`,`outcome`), `object_storage_requests_total` (ctr, `bucket`,`operation`,`outcome`), `object_storage_errors_total` (ctr, `error_code`), `object_storage_bucket_usage_bytes` (gauge) / `quota`.

**SMTP**: `smtp_delivery_duration_seconds` (hist, `provider`,`outcome`), `smtp_messages_total` (ctr, `provider`,`outcome`), `smtp_delivery_failures_total` (ctr, `error_class`), `smtp_connection_pool_used` (gauge) / `max`.

All labels bounded enums; no unbounded values.

---

## 8. Cross-service PromQL (dashboards)

E2E latency: `histogram_quantile(0.95, sum by (le) (rate(ticketing_fulfillment_request_duration_seconds_bucket[5m])))`

Error rate/op: `sum by (op) (rate(ticketing_fulfillment_requests_total{outcome="error"}[5m])) / sum by (op) (rate(ticketing_fulfillment_requests_total[5m]))`

Consumer lag: `max(ticketing_fulfillment_inbox_lag) by (consumer_group)`

---

## 9. Conventions checklist

- [x] `snake_case` + unit suffix; counters end `_total`
- [x] Labels bounded (no `user_id`, `email`, `reservation_id`, `ticket_id`)
- [x] `operation` = stable business values; `outcome` = `success`/`error`/`retry`
- [x] Buckets match per-op p99 with headroom
- [x] ≥3 real project elements per table (service, event, queue, table, bucket, endpoint)

---

## 10. Reflection (CP4)

**Learned:** Four golden signals ≠ generic counters. Latency=histogram with tailored buckets, traffic/errors=same counter filtered by `outcome`, saturation=gauges for queues/pools. Cardinality is hard constraint: unbounded labels explode TSDB → keep in logs (CP2), metrics use only bounded enums.

**Applies to my service:** `operation` splits fulfillment into `inbox_process`, `pdf_generate`, `pdf_store`, `email_send`, `event_publish` with different tails (email 30s). `ticketing_fulfillment_inbox_lag` tracks `cine.booking.booking-confirmed.v1` consumer lag. `outcome`=`success`/`error`/`retry` gives error rates per op without cardinality.

**First implement:** Add `ticketing_fulfillment_request_duration_seconds` (histogram) and `ticketing_fulfillment_requests_total` (counter) to Spring Boot `BookingConfirmedConsumer` + `TicketService`/`NotificationService` via Micrometer. Blocker: service has no custom metrics yet; QA lacks Prometheus scraper for `/actuator/prometheus`. Need team instrumentation + platform Prometheus deployment.