# Runbook — Ticketing Fulfillment Availability Fast Burn

**Alert**: `TicketingFulfillmentAvailabilityFastBurn`  
**SLO**: Availability 99.5% over 30d (ticketing-fulfillment-service)  
**Burn rate**: 14.4x over 1h (consumes 2% of monthly error budget per hour)  
**Severity**: `critical` — pages on-call  
**Runbook version**: 1.0  
**Last updated**: 2026-10-21  
**Owner**: Ticketing team (Nicolas Obregon, Tech Lead)

---

## 1. Symptoms

| Signal | Expected | Alert Threshold |
|---|---|---|
| Error rate (inbox_process + event_publish) | < 0.5% | > 0.72% for 5m |
| `ticketing_fulfillment_inbox_lag` | < 100 | Growing rapidly |
| `ticketing_fulfillment_requests_total{outcome="error"}` | Low, sporadic | Sustained spike |
| Customer impact | None | Users not receiving tickets |

**Typical manifestations**: Grafana red error panel; spike in `ERROR` logs (`ticket.email.timeout`, `event_publish.failed`); support tickets "no ticket email".

---

## 2. Confirmation

### 2.1 PromQL (copy-paste in Grafana/Prometheus)

**Error rate > 0.72% over 1h:**
```promql
sum(rate(ticketing_fulfillment_requests_total{operation=~"inbox_process|event_publish",outcome="error"}[1h]))
/
sum(rate(ticketing_fulfillment_requests_total{operation="inbox_process"}[1h]))
```

**Which operation failing:**
```promql
sum by (operation) (rate(ticketing_fulfillment_requests_total{operation=~"inbox_process|pdf_generate|pdf_store|email_send|event_publish",outcome="error"}[5m]))
```

**Consumer lag:**
```promql
max(ticketing_fulfillment_inbox_lag) by (consumer_group)
```

**Downstream checks:**
```promql
# SMTP
sum(rate(ticketing_fulfillment_requests_total{operation="email_send",outcome="error"}[5m]))
# Object storage
sum(rate(ticketing_fulfillment_requests_total{operation="pdf_store",outcome="error"}[5m]))
# RabbitMQ publish
sum(rate(ticketing_fulfillment_requests_total{operation="event_publish",outcome="error"}[5m]))
```

### 2.2 Log Query by `trace_id` (Loki)

```
{service="ticketing-fulfillment-service"} |= "ERROR" | trace_id="<trace_id_from_promql>"
```

**Patterns**: `ticket.email.timeout` (SMTP), `ticket.pdf.store.failed` (storage), `event_publish.failed` (RabbitMQ), `inbox_process.failed` (PostgreSQL). Same `trace_id` across errors = systemic.

---

## 3. Mitigations (Ordered by Risk — Lowest First)

### 🟢 1. Feature Flag: Disable Email Delivery (Lowest Risk)
**What**: Toggle `ticketing.email.delivery.enabled = false` (ConfigMap/Consul/LaunchDarkly).  
**Effect**: Skips `email_send`; tickets persist, PDFs stored, `TicketIssued` published. Users get tickets via `GET /api/v1/tickets/{booking_id}`.  
**Rollback**: Flip flag back (instant).  
**When**: First response if errors in `email_send` or `pdf_store`.

### 🟡 2. Circuit Breaker on SMTP / Object Storage (Medium Risk)
**What**: Enable Resilience4j circuit breaker on failing dependency (2s timeout, fallback = queue for retry).  
**Effect**: Fail fast, prevent thread exhaustion, allow DLQ retries.  
**Rollback**: Disable config (reload/redeploy).  
**When**: Mitigation 1 insufficient, or `pdf_store` errors.

### 🔴 3. Rollback to Previous Stable Version (High Risk)
**What**: Deploy previous known-good image (`v1.2.3`).  
```bash
kubectl set image deployment/ticketing-fulfillment-service ticketing-fulfillment=cinesync/ticketing-fulfillment-service:v1.2.3 -n cinesync-prod
```
**Effect**: Reverts all recent code changes.  
**Rollback**: Deploy forward.  
**When**: Mitigations 1+2 fail AND error rate persists >5 min; or `inbox_process`/`event_publish` failing (core bug).

### 🔴 4. Scale Consumer Pods (Last Resort)
**What**: Increase replicas to drain inbox lag.  
```bash
kubectl scale deployment ticketing-fulfillment-service --replicas=6 -n cinesync-prod
```
**Risk**: If downstream failing, more load = worse. Only if `inbox_lag` > 10k AND downstream healthy.

---

## 4. Escalation

| Time | Action | Responsible |
|---|---|---|
| 0 min | Alert fires → page | On-call |
| 0–5 min | Run confirmation queries | On-call |
| 5 min | Apply Mitigation 1 (feature flag) | On-call |
| 10 min | Apply Mitigation 2 (circuit breaker) | On-call + Tech Lead (Nicolas) |
| 20 min | Apply Mitigation 3 (rollback) | Tech Lead + EM |
| 30 min | Page EM, consider Mitigation 4 | EM + On-call |
| 60 min | War room (CTO notified) | EM + CTO |

**Contacts**: On-call (Nicolas) → `nicolas.obregon@cinesync.com` / +57 3XX XXX XXXX → EM → CTO  
**Channel**: `#incident-ticketing` (Slack) + Zoom link in topic.

---

## 5. Recovery Verification (All GREEN 15 min)

| Check | Query | Green Threshold |
|---|---|---|
| Error rate (1h) | PromQL §2.1 | < 0.1% |
| Error rate (5m) | Same, `[5m]` | 0% |
| Consumer lag | `max(ticketing_fulfillment_inbox_lag)` | < 100 |
| Issuance rate | `rate(ticketing_fulfillment_requests_total{operation="event_publish",outcome="success"}[5m])` | Matches `inbox_process` ±5% |
| Email (if re-enabled) | `rate(ticketing_fulfillment_requests_total{operation="email_send",outcome="success"}[5m])` | > 95% of `event_publish` |
| No new ERROR logs | `{service="ticketing-fulfillment-service"} |= "ERROR" [15m]` | 0 results |

**Dashboard**: https://grafana.cinesync.internal/d/ticketing-slo/ticketing-fulfillment-slo?orgId=1&from=now-30m&to=now

---

## 6. Post-Incident

1. **1h**: On-call writes timeline in `#incident-ticketing`.
2. **24h**: Tech Lead creates blameless postmortem (template `08-incident.md`): timeline, impact, root cause (5 Whys), contributing factors, 3 action items as user stories with acceptance criteria.
3. **Next sprint**: Action items in backlog, prioritized by EM.

---

## 7. Quick Reference (Pin)

```
FAST BURN → Ticketing Fulfillment
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. CHECK: Error rate > 0.72%? (PromQL)
2. CHECK: Which op? inbox | pdf | email | publish
3. CHECK: Lag? ticketing_fulfillment_inbox_lag
4. LOG: trace_id="..." → find pattern
5. FIX 1: Flag email.delivery.enabled=false
6. FIX 2: Circuit breaker SMTP / storage
7. FIX 3: Rollback to v1.2.3
8. FIX 4: Scale replicas (ONLY if lag high + downstream OK)
9. VERIFY: All green 15 min → close
10. POSTMORTEM: 24h → 3 action items
```

---

