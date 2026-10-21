# Critical Journey: Confirmed Booking to Ticket Fulfillment

## Scope

This journey follows a confirmed CineSync reservation into
`ticketing-fulfillment-service`, where the service consumes `BookingConfirmed`,
deduplicates the event, creates a digital ticket, stores its PDF, and attempts
email delivery. The service publishes `TicketIssued` after ticket creation.

The service documentation confirms event correlation metadata and Inbox
deduplication by `eventId`. It does not establish that W3C `traceparent` is
currently propagated through RabbitMQ. The trace propagation shown below is the
instrumentation target for this workshop, not a claim about existing behavior.

## Hop map

| Step | Caller | Receiver | Protocol | Operation | Sync/Async | Data or context |
|---|---|---|---|---|---|---|
| 1 | `booking-service` | Booking Outbox | PostgreSQL | Persist reservation confirmation and `BookingConfirmed` in the same transaction | Sync | `eventId`, `reservationId`, `userId`, `showtimeId`, seat and display snapshots, correlation metadata |
| 2 | `csp-worker` | Booking Outbox | PostgreSQL | Read the pending outbox event with read-only access | Sync | Event envelope and its correlation metadata |
| 3 | `csp-worker` | RabbitMQ | AMQP | Publish `BookingConfirmed` to `cine.booking.booking-confirmed.v1` | Async | Event envelope; propagate trace context in message headers as the target behavior |
| 4 | RabbitMQ | `ticketing-fulfillment-service` | AMQP | Deliver `BookingConfirmed` to the Ticketing consumer | Async | `eventId`, `reservationId`, event payload and propagated trace context |
| 5 | `ticketing-fulfillment-service` | `ticketing_fulfillment` PostgreSQL | SQL | Check/record `inbox_events` and persist the ticket and fulfillment state | Sync within event processing | `eventId`, `reservationId`, ticket data and delivery status |
| 6 | `ticketing-fulfillment-service` | Object storage | Storage API | Store the generated ticket PDF and retain its URL | Sync within fulfillment | Ticket/PDF data; record the resulting `pdfUrl` |
| 7 | `ticketing-fulfillment-service` | SMTP provider | SMTP | Attempt delivery of the ticket email | Sync dependency of the delivery attempt | Delivery result; do not log credentials or the full email address |
| 8 | `ticketing-fulfillment-service` | Event transport | Not specified by the service docs | Publish `TicketIssued` after durable ticket creation | Async | `ticketId`, `reservationId`, `pdfUrl`, `issuedAt` and event correlation metadata; trace headers are a propagation target |

## Sequence diagram

The `traceparent` labels below describe the intended instrumentation. The
project documents do not confirm that these headers are already injected and
extracted by `csp-worker`, RabbitMQ, or the Ticketing consumer.

```mermaid
sequenceDiagram
    participant Booking as booking-service
    participant Outbox as Booking Outbox
    participant Worker as csp-worker
    participant MQ as RabbitMQ
    participant Ticketing as ticketing-fulfillment-service
    participant DB as ticketing_fulfillment PostgreSQL
    participant Storage as Object storage
    participant SMTP as SMTP provider
    participant Events as TicketIssued event transport

    Booking->>Outbox: Confirm reservation and persist BookingConfirmed
    Worker->>Outbox: Read pending event (read-only)
    Worker-)MQ: Publish BookingConfirmed (traceparent in message headers; target)
    MQ-)Ticketing: Deliver event (same trace_id; target)
    Ticketing->>DB: Deduplicate eventId and persist ticket/fulfillment
    Ticketing->>Storage: Store ticket PDF
    Ticketing->>SMTP: Attempt fulfillment email
    Ticketing-)Events: Publish TicketIssued (trace context in headers; target)
```

## Span tree

Target span hierarchy when trace context is propagated through the asynchronous
message:

- `booking.confirmation` — root span for confirming the reservation and writing the Outbox event
  - `outbox.publish` — `csp-worker` publishes `BookingConfirmed`
    - `rabbitmq.consume BookingConfirmed` — Ticketing receives the event
      - `ticketing.inbox.process` — deduplicate by `eventId` and persist the ticket
      - `ticket.pdf.generate_and_store` — generate the PDF and store it
      - `ticket.email.send` — attempt SMTP delivery
      - `ticket.issued.publish` — publish `TicketIssued`

All spans in this target tree share one `trace_id`; each span has its own
`span_id`, and child spans record their parent span. The producer injects the
W3C trace context into the RabbitMQ message headers, and the consumer extracts
it to continue the trace. If the transport boundary cannot preserve a parent
relationship, create a new consumer trace and link it to the producer context
rather than silently dropping correlation.

`eventId` remains the Inbox idempotency key. `correlationId` and `causationId`
are business-event metadata. Neither replaces the tracing `trace_id`.

## Documentation gap to verify

The current project documents list `TicketIssued` as a published event, while
ADR-019 says Ticketing & Fulfillment has an Inbox only and publishes nothing
through an Outbox. Confirm the actual publication mechanism and transport for
`TicketIssued` with the team before treating the final hop as implemented.
Regardless of that outcome, the `BookingConfirmed` consumer path and its
trace-context propagation can be instrumented and tested independently.
