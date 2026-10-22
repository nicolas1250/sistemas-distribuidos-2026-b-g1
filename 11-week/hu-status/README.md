<!-- HU-STATUS TEMPLATE - do NOT remove the <!-- ... --> markers or the table headers.
     Your weekly grade is read AUTOMATICALLY from this file:
       11-week/hu-status/README.md  (inside YOUR fork). English. -->

# Weekly Status - Week 11

<!-- CONFIG-START - must match your profile repo (username/username) CONFIG -->
- FULL_NAME: nicolas obregon rojas
- GITHUB_USER: nicolas1250
- TEAM: CineSync platform
- SPRINT_GOAL: Implement end-to-end observability for the ticketing-fulfillment-service critical journey (BookingConfirmed -> TicketIssued), including trace propagation, structured logging, instrumentation, metric catalog with golden signals, SLOs with error budget, burn-rate alerting, runbook, and incident drill with postmortem.
<!-- CONFIG-END -->

## 1. User stories worked this week
| HU ID | Title | Status (todo/doing/done) | Evidence (PR or commit URL) |
|---|---|---|---|
| HU-TKT-001 | Observability instrumentation for ticketing-fulfillment-service | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/17c7258 |
| HU-TKT-002 | Structured log contract for ticketing domain | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/5f3e3e2 |
| HU-TKT-003 | Golden signals and metric catalog for ticketing journey | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/2a850dc |
| HU-TKT-004 | SLIs, SLOs, and error budget for ticketing fulfillment | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/XXX-cp6 |
| HU-TKT-005 | Burn-rate alerting rules for availability SLO | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/XXX-cp7 |
| HU-TKT-006 | Runbook for fast-burn availability alert | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/XXX-cp8 |
| HU-TKT-007 | Incident drill and blameless postmortem | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/XXX-cp9 |

## 2. My individual contribution
- **CP0**: Created workshop README.md with CONFIG block and reflection document link.
- **CP1**: Mapped critical journey (BookingConfirmed → ticketing-fulfillment-service → TicketIssued) with hop table, Mermaid sequence diagram showing traceparent propagation across RabbitMQ, and span tree.
- **CP2**: Defined structured log contract with 11 required fields + 8 domain fields (event_id, event_type, reservation_id, ticket_id, showtime_id, inbox_result, delivery_attempt, dependency, error_code), sensitive data rules, 7 example log lines sharing trace_id (including timeout and business-rule rejection), and log level policy.
- **CP3**: Built Java 21 demo (TicketingObservabilityDemo.java) with trace-context middleware (reads/creates W3C traceparent), structured logger matching CP2 contract, downstream propagation to mock storage with child span, Prometheus metrics endpoint (/metrics) with request counter and latency histogram, and automated test verifying trace_id propagation and metric updates.
- **CP4**: Created metric catalog with golden signals for ticketing-fulfillment-service (latency histogram, traffic/error counter by operation/outcome, saturation gauges for inbox lag and thread pool), 5 operations (inbox_process, pdf_generate, pdf_store, email_send, event_publish), bounded labels, histogram buckets justified per operation latency profile, plus condensed tables for 6 supporting services and cross-service PromQL snippets.
- **CP5**: Updated hu-status for delivery 1 with all CP0-CP4 evidence links.
- **CP6**: Defined three SLOs (Availability 99.5%, Latency p95 ≤5s, Issuance Success 99.0% over 30d) with exact PromQL from CP4 metrics, traffic estimate (8k events/day, 240k/month), error budget calculations (1,200 / 12,000 / 2,400 failures monthly), and team error budget policy at 50%/75%/100% consumption.
- **CP7**: Created burn-rate alert rules in 06-alerts.yml (fast 14.4x/1h, slow 6x/6h, combined multi-window) for availability SLO with exact metric names, labels (severity, team, slo, burn_rate, window), annotations (summary, description, runbook_url, dashboard_url), and alert policy in 06-alert-policy.md with budget math, two alerts NOT created (high latency, queue backlog) with justifications.
- **CP8**: Wrote runbook for fast-burn alert with symptoms, copy-pasteable PromQL confirmation queries, Loki log queries by trace_id, 4 mitigations ordered by risk (feature flag → circuit breaker → rollback → scale), escalation path with real team roles and timestamps, recovery verification checks, and post-incident actions.
- **CP9**: Simulated 40-min SMTP latency incident (3s at 25% requests) with trace (4,075 ms total, email_send 3,042 ms), 4 CP2-format log lines sharing trace_id, SLI impact (Availability 97.5%, Latency p95 72%, Issuance 97.5%, 4.6% budget consumed), alert firing at minute 7, mitigation 1 (feature flag) at minute 14 stopping damage, and blameless postmortem with timeline, impact, 5 Whys root cause, contributing factors, and 3 action items as user stories with acceptance criteria.

## 3. Blockers and risks
- QA environment lacks Prometheus scraper; /actuator/prometheus exposed but not ingested.
- ticketing-fulfillment-service has no custom Micrometer metrics yet (only default JVM/HTTP).
- Team needs to align on TicketIssued publication mechanism (ADR-019 vs docs discrepancy noted in CP1).
- SMTP provider metrics depend on external exporter (SendGrid/SES) not yet configured.
- Feature flags, circuit breakers, and log aggregation (Loki) not implemented in production code.
- No Alertmanager / PagerDuty integration for team yet (platform team owns alerting infra).

## 4. Plan for next week
- Implement Action 1 from postmortem: SMTP circuit breaker + 1s timeout + fallback queue (Resilience4j + email_retry_queue table).
- Implement Action 2: Email delivery feature flag via Spring Cloud Config / Consul.
- Implement Action 3: Decouple email from fulfillment transaction (async retry, reorder spans).
- Add custom Micrometer metrics (CP4) to production ticketing-fulfillment-service.
- Onboard service to Prometheus + Grafana in QA (coordinate with platform team).
- Configure Alertmanager routes + PagerDuty for ticketing team.

## 5. Compliance self-check
- [x] Conventional Commits - `type(scope): summary`
- [x] Per-environment HU branch + PR to that environment (hu-xxx-dev -> develop, ...)
- [x] Testable acceptance criteria
- [x] Tests added/updated (unit / integration)
- [x] DDD / hexagonal boundaries respected (domain has no I/O)
- [x] No secrets; config via environment variables

## 6. Evidence links
- CP0: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/3b33f49
- CP1: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/0cc1522
- CP2: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/5f3e3e2
- CP3 (push 1): https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/17c7258
- CP3 (push 2): https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/5c2a370
- CP4: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/2a850dc
- CP6: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/XXX-cp6
- CP7: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/XXX-cp7
- CP8: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/XXX-cp8
- CP9: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/XXX-cp9
- Workshop folder: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/tree/main/11-week/workshop
- Reflection doc: https://docs.google.com/document/d/1HEIymSqpVxZ2z69av__kO8ZxZqxQC-pKo38jwUn-HYU/edit?tab=t.0