# Incident Drill & Postmortem — Ticketing Fulfillment SMTP Latency Degradation

## Incident Scenario

**Trigger**: SMTP provider responds in **3s for 25% of requests** for **40 min**.  
**Affected operation**: `email_send` (CP1 Step 7).  
**Window**: 2026-10-21 02:15–02:55 UTC.

---

## 1. Slow Request Trace (Span Tree)

**Trace ID**: `a1b2c3d4e5f67890123456789abcdef0`  
**Correlation ID**: `corr-booking-20261021-0042`  
**eventId**: `evt-booking-778899`

```
- booking.confirmation — 45 ms
  - outbox.publish — 12 ms
    - rabbitmq.consume — 3 ms
      - ticketing.inbox.process — 38 ms
      - ticket.pdf.generate_and_store — 840 ms
      - ticket.email.send — 3,042 ms  ← SLOW (3s, 25th %ile)
      - ticket.issued.publish — 55 ms
```

**Total**: 4,075 ms (normal p95 ~1,200 ms). 11 spans share `trace_id`.
---
## 2. Four Log Lines (CP2 Format)

All share `trace_id="a1b2c3d4e5f67890123456789abcdef0"`, `correlation_id="corr-booking-20261021-0042"`

```json
{"timestamp":"2026-10-21T02:18:23.100Z","level":"INFO","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"prod","trace_id":"a1b2c3d4e5f67890123456789abcdef0","span_id":"11a2b3c4d5e6f789","correlation_id":"corr-booking-20261021-0042","event":"booking.confirmed.received","message":"BookingConfirmed event received","duration_ms":3,"event_id":"evt-booking-778899","event_type":"BookingConfirmed","reservation_id":"res-20261021-0042","inbox_result":"new"}
{"timestamp":"2026-10-21T02:18:23.940Z","level":"INFO","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"prod","trace_id":"a1b2c3d4e5f67890123456789abcdef0","span_id":"22b3c4d5e6f78901","correlation_id":"corr-booking-20261021-0042","event":"ticket.pdf.stored","message":"Ticket PDF stored in object storage","duration_ms":840,"reservation_id":"res-20261021-0042","ticket_id":"tkt-20261021-0042","dependency":"object_storage"}
{"timestamp":"2026-10-21T02:18:26.982Z","level":"ERROR","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"prod","trace_id":"a1b2c3d4e5f67890123456789abcdef0","span_id":"33c4d5e6f7890123","correlation_id":"corr-booking-20261021-0042","event":"ticket.email.timeout","message":"SMTP delivery timed out after 3s","duration_ms":3042,"reservation_id":"res-20261021-0042","ticket_id":"tkt-20261021-0042","dependency":"smtp","delivery_attempt":1,"error_code":"SMTP_TIMEOUT"}
{"timestamp":"2026-10-21T02:18:27.037Z","level":"ERROR","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"prod","trace_id":"a1b2c3d4e5f67890123456789abcdef0","span_id":"44d5e6f789012345","correlation_id":"corr-booking-20261021-0042","event":"fulfillment.failed","message":"Ticket fulfillment failed due to email timeout; TicketIssued not published","duration_ms":55,"reservation_id":"res-20261021-0042","ticket_id":"tkt-20261021-0042","error_code":"EMAIL_SEND_FAILED","inbox_result":"processed"}
```

---

## 3. SLI Values During Incident (40 min)

| SLI | Normal | Incident | Calculation |
|---|---|---|---|
| **Availability** | 99.8% | **97.5%** | 55/222 failed = 24.8% |
| **Latency p95 ≤5s** | 95% | **72%** | 25% >5s total |
| **Issuance Success** | 99.5% | **97.5%** | Same as availability |

**Error budget**: 55 failures / 1,200 monthly = **4.6% consumed in 40 min** (~9.9 min equivalent downtime).

---

## 4. Alert Timeline

| Min | Event |
|---|---|
| 0 | Incident starts |
| 2 | 1h error rate > 0.72% |
| 5 | Fast burn condition met |
| **7** | **`TicketingFulfillmentAvailabilityFastBurn` FIRES** (page) |
| 10 | On-call acknowledges |
| 12 | Identifies `email_send` failing |
| **14** | **Mitigation 1 applied**: feature flag `ticketing.email.delivery.enabled=false` |
| 15 | Error rate → 0% |
| 40 | SMTP recovers |
| 45 | Flag re-enabled; verified |

**First alert**: Fast burn at **minute 7** (5m confirmation).
---
## 5. Runbook Step That Stopped Damage

**Mitigation 1 (Feature Flag)** at minute 14: `ticketing.email.delivery.enabled=false`.

- Removed SMTP from critical path instantly (config toggle)
- `email_send` skipped → fulfillment completes → `TicketIssued` published
- Users get tickets via web portal (`GET /api/v1/tickets/{booking_id}`)
- Error budget burn stopped; remaining 26 min near-zero impact.
---
## 6. Blameless Postmortem

### Timeline
| Time | Event |
|---|---|
| 02:15 | SMTP queue congestion (noisy neighbor on shared IP) |
| 02:17 | First `SMTP_TIMEOUT` logs |
| 02:19 | Error rate >1%; lag growing |
| 02:22 | Fast burn threshold crossed |
| **02:27** | **Fast burn alert fires** |
| 02:30 | On-call confirms; finds `email_send` |
| 02:32 | Feature flag toggled |
| 02:33 | Error rate baseline; lag drains |
| 02:55 | SMTP recovers |
| 02:57 | Flag re-enabled |

### Impact
- **Duration**: 40 min
- **Events**: 222 processed, 55 failed (24.8%)
- **Budget consumed**: 4.6% monthly (55/1,200)
- **Customer impact**: 55 users no email during incident; tickets in portal after mitigation
- **Support**: ~12 "missing email" tickets

### Root Cause (5 Whys)
1. Fulfillments failed → `email_send` timeout aborted transaction before `event_publish`
2. SMTP timeout → SendGrid queue congestion (shared IP, noisy neighbor)
3. 25% affected → rate limiting on our IP pool
4. 3s delays not fast failures → SendGrid tar-pits (slow response) vs 4xx
5. **No resilience**: 3s default timeout, no circuit breaker, no feature flag, email in critical path

### Contributing Factors
- No circuit breaker on SMTP
- No feature flag for email
- 3s timeout matched tar-pit exactly
- No SMTP latency histogram in prod (CP4 metric missing)
- Single SMTP provider, no fallback
- Email coupled in fulfillment transaction
---
## 7. Action Items (User Stories + Acceptance Criteria)

### 1. SMTP Circuit Breaker & Tuned Timeouts
**Story**: *As a ticketing engineer, I want SMTP calls protected by circuit breaker with 1s timeout + fallback queue, so slow SMTP doesn't block fulfillment.*

**Acceptance Criteria**:
- [ ] Resilience4j on `EmailService`; timeout 1s (`spring.mail.timeout=1000`)
- [ ] Fallback: persist to `email_retry_queue` table, return success to fulfillment
- [ ] Unit test: mock SMTP >1s → circuit opens → fallback → fulfillment completes
- [ ] Integration test: FastBurn does NOT fire with 3s SMTP latency at 25%

### 2. Email Delivery Feature Flag
**Story**: *As on-call, I want a feature flag to disable email instantly, so I preserve issuance during SMTP outages.*

**Acceptance Criteria**:
- [ ] `ticketing.email.delivery.enabled` (default `true`) via Spring Cloud Config
- [ ] `EmailService.send()` checks flag; if `false`, logs `email.skipped`, returns
- [ ] Toggle via ConfigMap reload <5s; verified in QA
- [ ] Runbook updated with exact flag path/command
- [ ] Drill: toggle during load test → error rate 0% in 30s

### 3. Decouple Email from Fulfillment (Async Retry)
**Story**: *As PO, I want ticket issuance and independent email, so SMTP failures never prevent `TicketIssued`.*

**Acceptance Criteria**:
- [ ] Reorder: `event_publish` BEFORE `email_send` in span tree
- [ ] Email async via `@Async` or `SendTicketEmail` message
- [ ] `email_retry_queue` table: `attempts`, `next_retry_at`, `last_error`
- [ ] Scheduler retries 5 min (max 3) → DLQ
- [ ] Issuance SLI = `TicketIssued published` (email excluded)
- [ ] Availability SLO raised to 99.9%
---