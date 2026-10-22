# Alert Policy — Ticketing Fulfillment Availability SLO

## SLO Reference

- **Service**: `ticketing-fulfillment-service`
- **SLO**: Availability 99.5% over 30 days (rolling)
- **SLI**: `Good fulfillments / BookingConfirmed consumed`
  - Good = `inbox_process` success AND `event_publish` success
  - Valid = `inbox_process` attempts
- **Error budget**: 0.5% = 1,200 failed fulfillments / 30 days = 2,160 minutes downtime equivalent

---

## Burn Rate Alerts (defined in `06-alerts.yml`)

### 1. Fast Burn — `TicketingFulfillmentAvailabilityFastBurn`

| Parameter | Value |
|---|---|
| **Burn rate** | 14.4x |
| **Window** | 1 hour |
| **For (confirmation)** | 5 minutes |
| **Severity** | `critical` (pages on-call) |
| **Budget consumed per firing hour** | **2% of monthly budget** (24 / 1,200 = 24 failures ≈ 2%) |
| **Math** | 14.4 × (0.5% / 720h) = 0.01% error rate per hour → 0.01% × 240,000 events/month ≈ 24 failures/hour |

**When it fires:** Error rate > 0.72% sustained for 5 minutes (14.4 × 0.5% = 0.72%)

**Action:** Immediate investigation. Page on-call. Follow runbook (`07-runbook.md`).

---

### 2. Slow Burn — `TicketingFulfillmentAvailabilitySlowBurn`

| Parameter | Value |
|---|---|
| **Burn rate** | 6x |
| **Window** | 6 hours |
| **For (confirmation)** | 30 minutes |
| **Severity** | `warning` (ticket + Slack, no page) |
| **Budget consumed per 6h firing** | **5% of monthly budget** (60 / 1,200 = 60 failures ≈ 5%) |
| **Math** | 6 × (0.5% / 720h) = 0.0042% error rate per hour → 0.0042% × 240,000 × 6h ≈ 60 failures/6h |

**When it fires:** Error rate > 0.3% sustained for 30 minutes (6 × 0.5% = 3% over 6h = 0.3%/h)

**Action:** Create ticket, investigate in business hours. Escalate to critical if persists >2h.

---

### 3. Combined Alert — `TicketingFulfillmentAvailabilityBurnRate`

- **Purpose**: Single paging rule that fires if EITHER fast OR slow condition met
- **For**: 2 minutes (faster confirmation for paging path)
- **Severity**: `critical`
- **Use**: Routes to PagerDuty / Opsgenie for on-call notification

---

## Budget Consumption Summary

| Alert | Burn Rate | Window | Budget % per Window | Fires When Error Rate > |
|---|---|---|---|---|
| Fast | 14.4x | 1h | 2% | 0.72% |
| Slow | 6x | 6h | 5% | 0.3% (avg over 6h) |

**Policy**: 
- Fast burn = **page immediately** (2% budget/hour is unsustainable)
- Slow burn = **ticket + investigate** (5% budget/6h is concerning but not emergency)
- Both firing = **page + war room** (compound burn)

---

## Alerts NOT Created (and Why)

### ❌ 1. `TicketingFulfillmentHighLatency` — p95 > 5s for 5m

**Why not**: 
- Latency SLO (p95 ≤ 5s) has a **separate, larger error budget** (12,000 slow events/month = 5%).
- High latency ≠ unavailability. Users get tickets eventually; they're just slow.
- Alerting on latency burns the *latency* budget, not the *availability* budget. Mixing them confuses on-call.
- **Instead**: Latency SLO gets its own burn-rate alerts in a separate rule group (future work). For now, latency is dashboard-monitored, not paged.

### ❌ 2. `RabbitMQQueueBacklog` — `rabbitmq_queue_messages_ready > 10,000`

**Why not**:
- Queue depth is a **leading indicator**, not a user-visible symptom. A backlog can drain without SLO breach.
- Threshold (10,000) is arbitrary — depends on traffic. At 8k events/day, 10k is ~1.25 days; at peak 3×, it's hours.
- Paging on queue depth causes **alert fatigue** during traffic spikes that self-resolve.
- **Instead**: Queue depth is a **dashboard panel** and a **runbook diagnostic step** ("check consumer lag"). The *consumer lag* saturation metric (`ticketing_fulfillment_inbox_lag`) is already in the metric catalog and feeds the runbook confirmation queries.

---

## Alert Routing & Escalation

| Alert | Route | Escalation |
|---|---|---|
| FastBurn | PagerDuty → On-call (ticketing team) | +15 min → Tech Lead → +30 min → EM |
| SlowBurn | Slack #ticketing-alerts + Jira ticket | +2h → Tech Lead → +4h → EM |
| Combined | PagerDuty (same as FastBurn) | Same as FastBurn |

**Inhibit rules** (to avoid flapping):
- Inhibit `SlowBurn` if `FastBurn` is firing (same SLO, faster window supersedes)
- Inhibit both if `TicketingFulfillmentAvailabilityBurnRate` is firing (combined rule handles paging)

---

## Validation Checklist

- [x] YAML validates with `promtool check rules 06-alerts.yml`
- [x] Metric names match `04-metrics.md` exactly (`ticketing_fulfillment_requests_total`, labels `operation`, `outcome`)
- [x] Labels include `severity`, `team`, `slo`, `burn_rate`, `window`
- [x] Annotations include `summary`, `description`, `runbook_url`, `dashboard_url`
- [x] `for` durations reasonable (5m fast, 30m slow) to avoid flapping
- [x] Budget math documented and matches CP6 (99.5% → 0.5% error budget)
- [x] Two "not created" alerts justified with project-specific reasoning

---

