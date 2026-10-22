# SLIs, SLOs, and Error Budget — Ticketing & Fulfillment Journey

## Scope

Three SLOs for the critical journey: `BookingConfirmed` → `ticketing-fulfillment-service` → `TicketIssued`. All PromQL uses metrics from `04-metrics.md`. Window: **30 days (2,592,000 seconds)** unless noted.

---

## 1. Traffic Estimate (justification)

| Metric | Value | Source |
|---|---|---|
| Peak showtimes/day | 200 | 20 screens × 10 showtimes |
| Avg tickets/showtime | 40 | 80-seat theater, 50% occupancy |
| **Daily `BookingConfirmed` events** | **8,000** | 200 × 40 |
| Monthly events (30d) | **240,000** | 8,000 × 30 |
| Peak hour events | ~1,000 | 8,000 / 8 active hours |

Assumptions: steady demand, no flash sales. If a blockbuster drives 3× peak, error budget burns faster — alerting (CP7) catches it.

---

## 2. SLO Definitions

### SLO 1 — Availability (consumer → producer)

**SLI formula:**  
`Good events / Valid events`  
= `BookingConfirmed consumed AND TicketIssued published successfully` / `BookingConfirmed delivered to consumer`

**PromQL (30d):**
```promql
sum(rate(ticketing_fulfillment_requests_total{operation=~"inbox_process|event_publish",outcome="success"}[30d]))
/
sum(rate(ticketing_fulfillment_requests_total{operation="inbox_process"}[30d]))
```

**Target:** **99.5%** (0.5% error budget)  
**Window:** 30 days rolling

**Error budget (monthly):**
- Valid events: 240,000
- Allowed failures: 240,000 × 0.005 = **1,200 failed fulfillments**
- Allowed downtime: 30d × 24h × 60m × 0.005 = **2,160 minutes (36 hours)**

---

### SLO 2 — Latency (p95 end-to-end)

**SLI formula:**  
`Proportion of fulfillments completing ≤ 5s`  
= `count(duration ≤ 5s) / count(all fulfillments)`

**PromQL (30d):**
```promql
histogram_quantile(0.95,
  sum by (le) (rate(ticketing_fulfillment_request_duration_seconds_bucket{operation=~"inbox_process|pdf_generate|pdf_store|email_send|event_publish"}[30d]))
) ≤ 5
```

**Target:** **p95 ≤ 5 seconds** (95% of fulfillments finish within 5s)  
**Window:** 30 days rolling

**Error budget (monthly):**
- Valid events: 240,000
- Allowed slow fulfillments: 240,000 × 0.05 = **12,000 fulfillments > 5s**
- In minutes of "degraded time": 12,000 / (240,000/30d) ≈ **1.5 days equivalent** at peak rate

---

### SLO 3 — Ticket Issuance Success Rate (business outcome)

**SLI formula:**  
`Tickets issued & email delivered / BookingConfirmed consumed`  
Captures end-to-end business success (PDF stored + email sent + event published).

**PromQL (30d):**
```promql
sum(rate(ticketing_fulfillment_requests_total{operation="event_publish",outcome="success"}[30d]))
/
sum(rate(ticketing_fulfillment_requests_total{operation="inbox_process"}[30d]))
```

**Target:** **99.0%** (1% error budget — stricter because failed issuance = angry customer + support ticket)  
**Window:** 30 days rolling

**Error budget (monthly):**
- Valid events: 240,000
- Allowed failed issuances: 240,000 × 0.01 = **2,400 failed ticket deliveries**
- In minutes: 30d × 24h × 60m × 0.01 = **4,320 minutes (72 hours)**

---

## 3. Error Budget Policy (team agreement)

| Consumption | Action | Owner | Deadline |
|---|---|---|---|
| **50%** (600 avail failures / 6,000 latency breaches / 1,200 issuance failures) | **Investigate & document**: root cause analysis in next retro; add mitigation to sprint backlog. | Tech Lead + PO | Within 2 business days |
| **75%** (900 / 9,000 / 1,800) | **Freeze non-critical features**: only bug fixes and SLO-related work allowed. Deploy hotfix if root cause known. | EM + Tech Lead | Immediate |
| **100%** (1,200 / 12,000 / 2,400) | **All-hands stabilization**: stop all feature work, full team on remediation, daily war room until budget recovers <50%. | EM + CTO | Immediate |

**Recovery:** Budget recovers as good events accumulate (rolling 30d window). No "budget reset" — only time heals.

**Escalation path:**  
On-call → Tech Lead → Engineering Manager → CTO (if 100% consumed >24h)

---

## 4. Reflection Entry for CP6

**What I learned in this checkpoint that I didn't know before:**

I learned the difference between an SLI (a measurable ratio), an SLO (a target on that ratio over a time window), and the error budget (the inverse — how much failure we can absorb). The key insight: **error budget is a shared currency between product and engineering**. It quantifies "how much unreliability we can afford before we must stop shipping features." I also learned that burn-rate alerting (CP7) is derived directly from the error budget math, not from arbitrary thresholds.

**How it applies to my project, with a concrete example from my services:**

For `ticketing-fulfillment-service`, the Availability SLO (99.5%) means: of every 240k `BookingConfirmed` events/month, at most 1,200 can fail to produce a `TicketIssued`. The Latency SLO (p95 ≤ 5s) uses the histogram from CP4 across all 5 operations (`inbox_process` through `event_publish`). The business SLO (99% issuance success) is stricter because a failed email or PDF store still counts as "ticket not delivered" from the user's perspective. The PromQL for each SLI directly references `ticketing_fulfillment_requests_total` and `ticketing_fulfillment_request_duration_seconds` with the `operation` and `outcome` labels I defined.

**What I would implement first in my team's real code and what stops us today:**

First, I'd add the three SLI queries as Grafana dashboard panels and recording rules in Prometheus (e.g., `slo:ticketing_availability:ratio_30d`). The blocker: **no Prometheus stack in QA yet** — the platform team hasn't deployed it. Also, the Spring Boot service exposes `/actuator/prometheus` but with only default JVM metrics; the custom `ticketing_fulfillment_*` metrics from CP4 don't exist in production code. We need: (1) Micrometer `Timer`/`Counter` instrumentation in `BookingConfirmedConsumer` and `TicketService`, (2) Prometheus + Grafana in QA, (3) recording rules for the SLI ratios so dashboards and alerts are fast.