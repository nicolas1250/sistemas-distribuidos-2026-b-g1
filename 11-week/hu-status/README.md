<!-- HU-STATUS TEMPLATE - do NOT remove the <!-- ... --> markers or the table headers.
     Your weekly grade is read AUTOMATICALLY from this file:
       11-week/hu-status/README.md  (inside YOUR fork). English. -->

# Weekly Status - Week 11

<!-- CONFIG-START - must match your profile repo (username/username) CONFIG -->
- FULL_NAME: nicolas obregon rojas
- GITHUB_USER: nicolas1250
- TEAM: CineSync platform
- SPRINT_GOAL: Implement end-to-end observability for the ticketing-fulfillment-service critical journey (BookingConfirmed -> TicketIssued), including trace propagation, structured logging, instrumentation, and metric catalog with golden signals.
<!-- CONFIG-END -->

## 1. User stories worked this week
| HU ID | Title | Status (todo/doing/done) | Evidence (PR or commit URL) |
|---|---|---|---|
| HU-TKT-001 | Observability instrumentation for ticketing-fulfillment-service | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/17c7258 |
| HU-TKT-002 | Structured log contract for ticketing domain | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/5f3e3e2 |
| HU-TKT-003 | Golden signals and metric catalog for ticketing journey | done | https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/commit/2a850dc |

## 2. My individual contribution
- CP0: Created workshop README.md with CONFIG block and reflection document link.
- CP1: Mapped critical journey (BookingConfirmed -> ticketing-fulfillment-service -> TicketIssued) with hop table, Mermaid sequence diagram showing traceparent propagation across RabbitMQ, and span tree.
- CP2: Defined structured log contract with 11 required fields + 8 domain fields (event_id, event_type, reservation_id, ticket_id, showtime_id, inbox_result, delivery_attempt, dependency, error_code), sensitive data rules, 7 example log lines sharing trace_id (including timeout and business-rule rejection), and log level policy.
- CP3: Built Java 21 demo (TicketingObservabilityDemo.java) with trace-context middleware (reads/creates W3C traceparent), structured logger matching CP2 contract, downstream propagation to mock storage with child span, Prometheus metrics endpoint (/metrics) with request counter and latency histogram, and automated test verifying trace_id propagation and metric updates.
- CP4: Created metric catalog with golden signals for ticketing-fulfillment-service (latency histogram, traffic/error counter by operation/outcome, saturation gauges for inbox lag and thread pool), 5 operations (inbox_process, pdf_generate, pdf_store, email_send, event_publish), bounded labels, histogram buckets justified per operation latency profile, plus condensed tables for 6 supporting services (booking-service, csp-worker, RabbitMQ, PostgreSQL, object storage, SMTP) and cross-service PromQL snippets.

## 3. Blockers and risks
- QA environment lacks Prometheus scraper; /actuator/prometheus exposed but not ingested.
- ticketing-fulfillment-service has no custom Micrometer metrics yet (only default JVM/HTTP).
- Team needs to align on TicketIssued publication mechanism (ADR-019 vs docs discrepancy noted in CP1).
- SMTP provider metrics depend on external exporter (SendGrid/SES) not yet configured.

## 4. Plan for next week
- CP6: Define SLIs/SLOs/error budget for availability, latency, and ticket-issuance success rate using CP4 metrics.
- CP7: Create burn-rate alert rules (fast 14.4x/1h, slow 6x/6h) for availability SLO in 06-alerts.yml and alert policy in 06-alert-policy.md.
- CP8: Write runbook for fast-burn alert with symptoms, confirmation queries, mitigations (feature flag, rollback, circuit breaker), escalation, and recovery verification.
- CP9: Simulate incident (downstream dependency 3s latency for 25% requests x 40min) with trace, logs, SLI impact, alert timing, runbook step that stopped damage, and blameless postmortem.
- CP10: Final reflection implementation plan and hu-status update for delivery 2.

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
- Workshop folder: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/tree/main/11-week/workshop
- Reflection doc: https://docs.google.com/document/d/1HEIymSqpVxZ2z69av__kO8ZxZqxQC-pKo38jwUn-HYU/edit?tab=t.0