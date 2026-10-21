# CP3 — Ticketing Observability Demo

This minimal Java 21 demo represents the `ticketing-fulfillment-service` path:
an HTTP filter creates or reads W3C `traceparent`, a structured logger records
the CP2 fields, and a synthetic PDF-storage request receives the same trace ID
with a child span. The local API backend was not available, so this is workshop
evidence, not a claim that the remote service is already instrumented.

## Run the automated integration test

From this folder, using PowerShell:

```powershell
javac --add-modules jdk.httpserver TicketingObservabilityDemo.java
java --add-modules jdk.httpserver TicketingObservabilityDemo --test
```

The test starts local HTTP endpoints, sends a request with a known trace ID,
verifies that the downstream storage request receives that trace ID, and checks
that the request counter and latency histogram are updated.

## Run the demo and inspect metrics

```powershell
java --add-modules jdk.httpserver TicketingObservabilityDemo
```

In another terminal:

```powershell
curl.exe -X POST http://127.0.0.1:8080/tickets/confirm
curl.exe http://127.0.0.1:8080/metrics
```

The demo uses synthetic ticket data only. It does not connect to RabbitMQ,
PostgreSQL, SMTP, or object storage. Adapt its filter and downstream propagation
to the real service integration before production use.

## Verified console output

```text
PASS: incoming trace_id propagated to downstream storage; counter and histogram updated.
```
