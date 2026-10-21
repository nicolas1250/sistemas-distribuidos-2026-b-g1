# Structured Log Contract — Ticketing & Fulfillment

## Scope

This contract applies to `ticketing-fulfillment-service` while it consumes
`BookingConfirmed`, records the event in `ticketing_fulfillment.inbox_events`,
creates a ticket, stores its PDF, and attempts email delivery. It also covers
the optional association of `ConcessionOrderConfirmed` with a reservation.

Every application log line is one JSON object written to standard output. Field
names use `snake_case`; timestamps are ISO-8601 UTC; durations use milliseconds.
The examples below are synthetic examples of the proposed contract, not output
captured from a running service.

## Field contract

| Field | Type | Required? | Example | Purpose |
|---|---|---|---|---|
| `timestamp` | string, ISO-8601 UTC | Required | `2026-10-20T19:30:00.100Z` | Orders events consistently across services and machines. |
| `level` | string enum | Required | `INFO` | Supports severity filtering. Allowed values: `ERROR`, `WARN`, `INFO`, `DEBUG`. |
| `service` | string | Required | `ticketing-fulfillment-service` | Identifies the emitting component. |
| `version` | string | Required | `1.2.0` | Connects behavior to the deployed build. Replace the example with the actual runtime version. |
| `environment` | string | Required | `qa` | Identifies the deployment environment without relying on host names. |
| `trace_id` | string, 32 lowercase hex characters | Required | `4bf92f3577b34da6a3ce929d0e0e4736` | Connects spans and logs across the request or propagated event flow. |
| `span_id` | string, 16 lowercase hex characters | Required | `00f067aa0ba902b7` | Identifies the operation that emitted this log line. |
| `correlation_id` | string | Required | `9a8349ee-4fc3-42de-b2d2-791dfbb71c21` | Connects related business events and supports the documented correlation metadata. It is not a replacement for `trace_id`. |
| `event` | string, dotted event name | Required | `ticket.email.timeout` | Machine-readable name for the operation or outcome. |
| `message` | string | Required | `SMTP delivery timed out` | Short human-readable explanation; must not contain secrets or raw personal data. |
| `duration_ms` | number | Required | `1200` | Elapsed time for the operation represented by the log. Use `0` when the event is instantaneous. |
| `event_id` | string, UUID | Optional | `a9a7dce1-5e8a-43bc-8e29-e3b5de375f83` | Identifies the consumed domain event and supports Inbox deduplication. |
| `event_type` | string | Optional | `BookingConfirmed` | Names the event being consumed or published. |
| `reservation_id` | string, UUID | Optional | `79a22ea5-115d-4ec0-9564-4ea84c4fa592` | Connects ticket fulfillment to the reservation without logging its full event payload. |
| `ticket_id` | string, UUID | Optional | `6b42f812-ae8e-4d86-b5fb-1c93c1f86b59` | Identifies the locally issued ticket. |
| `showtime_id` | string, UUID | Optional | `356a192b-7913-4b84-9f40-bc1c55a5632a` | Identifies the showtime associated with the ticket. |
| `inbox_result` | string enum | Optional | `processed` | Records whether the Inbox event was processed, already seen, or deferred. |
| `delivery_attempt` | integer | Optional | `2` | Shows which fulfillment email attempt produced the log. |
| `dependency` | string enum | Optional | `smtp` | Identifies a bounded downstream dependency, such as `smtp`, `postgres`, `rabbitmq`, or `object_storage`. |
| `error_code` | string | Optional | `SMTP_TIMEOUT` | Stable, machine-readable failure reason; do not put provider response bodies here. |

Do not use unbounded values such as raw URLs, email addresses, names, or
arbitrary exception messages as metric labels. Logs may include a stable error
code and a sanitized message instead.

## Sensitive data rules

| Data | Logging rule | Safe alternative |
|---|---|---|
| Email address in `user_contacts` | Never log the full address or its local part. Avoid logging the address entirely. | Use `user_id` only when operationally necessary and access-controlled; otherwise log the outcome and `reservation_id`. |
| JWTs, access tokens, refresh tokens, service credentials, SMTP credentials | Never log, including authorization headers or secret values in exceptions. | Log `auth_result` or `dependency` and a stable error code. |
| Passwords, reset tokens, email verification codes | Never log request bodies or these values. | Log the operation name and outcome only. |
| `qr_payload`, QR image bytes, PDF contents, or signed object-storage URLs | Never log payloads, file bytes, or URLs that grant access. | Log `ticket_id`, storage operation, and a sanitized result such as `stored` or `failed`. |
| Raw event payloads | Do not log the whole payload; it can contain identity or booking data. | Log `event_id`, `event_type`, `reservation_id`, and a sanitized validation/error code. |
| Payment data | Ticketing does not own payment processing; never log payment credentials or card data if they appear in an unexpected payload. | Log only the event identifier and a schema-validation outcome. |

If an email address must be shown in a controlled diagnostic, use a one-way
reference or a masked form such as `***@example.com`; do not include the local
part. Prefer not to log it at all.

## Seven example log lines

All seven records share the same `trace_id` and `correlation_id`. Span IDs are
different because each line belongs to a different operation. These records
illustrate a synthetic trace for a confirmed reservation with two failure
signals: an optional concession-association business-rule rejection and an
SMTP timeout followed by a successful retry.

The project documents say that concession details are associated by
`reservationId`, but do not define a rejection policy for a mismatched
association. The rejection below is therefore an example rule to confirm with
the team, not a claim about existing runtime behavior.

```json
{"timestamp":"2026-10-20T19:30:00.100Z","level":"INFO","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"qa","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"00f067aa0ba902b7","correlation_id":"9a8349ee-4fc3-42de-b2d2-791dfbb71c21","event":"booking.confirmed.received","message":"BookingConfirmed event received","duration_ms":4,"event_id":"a9a7dce1-5e8a-43bc-8e29-e3b5de375f83","event_type":"BookingConfirmed","reservation_id":"79a22ea5-115d-4ec0-9564-4ea84c4fa592","inbox_result":"new"}
{"timestamp":"2026-10-20T19:30:00.140Z","level":"INFO","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"qa","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"f4c1b3a2d5e60718","correlation_id":"9a8349ee-4fc3-42de-b2d2-791dfbb71c21","event":"ticket.persisted","message":"Ticket and Inbox record persisted","duration_ms":36,"event_id":"a9a7dce1-5e8a-43bc-8e29-e3b5de375f83","event_type":"BookingConfirmed","reservation_id":"79a22ea5-115d-4ec0-9564-4ea84c4fa592","ticket_id":"6b42f812-ae8e-4d86-b5fb-1c93c1f86b59","inbox_result":"processed"}
{"timestamp":"2026-10-20T19:30:00.190Z","level":"WARN","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"qa","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"29ac7301b4d8e652","correlation_id":"9a8349ee-4fc3-42de-b2d2-791dfbb71c21","event":"concession.association.rejected","message":"Concession fulfillment was not attached because its reservation reference did not match","duration_ms":8,"event_id":"ec8a5104-a2b3-4ca8-b365-5f3353de9e1a","event_type":"ConcessionOrderConfirmed","reservation_id":"79a22ea5-115d-4ec0-9564-4ea84c4fa592","error_code":"RESERVATION_ASSOCIATION_MISMATCH"}
{"timestamp":"2026-10-20T19:30:00.310Z","level":"INFO","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"qa","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"5d820ca931b4f706","correlation_id":"9a8349ee-4fc3-42de-b2d2-791dfbb71c21","event":"ticket.pdf.stored","message":"Ticket PDF stored in object storage","duration_ms":112,"reservation_id":"79a22ea5-115d-4ec0-9564-4ea84c4fa592","ticket_id":"6b42f812-ae8e-4d86-b5fb-1c93c1f86b59","dependency":"object_storage"}
{"timestamp":"2026-10-20T19:30:00.430Z","level":"INFO","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"qa","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"831b2f4c5d6e7081","correlation_id":"9a8349ee-4fc3-42de-b2d2-791dfbb71c21","event":"ticket.issued","message":"TicketIssued published after ticket and Inbox transaction completed","duration_ms":12,"event_type":"TicketIssued","reservation_id":"79a22ea5-115d-4ec0-9564-4ea84c4fa592","ticket_id":"6b42f812-ae8e-4d86-b5fb-1c93c1f86b59"}
{"timestamp":"2026-10-20T19:30:00.450Z","level":"ERROR","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"qa","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"27d40a6be98f135c","correlation_id":"9a8349ee-4fc3-42de-b2d2-791dfbb71c21","event":"ticket.email.timeout","message":"SMTP delivery timed out","duration_ms":1200,"reservation_id":"79a22ea5-115d-4ec0-9564-4ea84c4fa592","ticket_id":"6b42f812-ae8e-4d86-b5fb-1c93c1f86b59","dependency":"smtp","delivery_attempt":1,"error_code":"SMTP_TIMEOUT"}
{"timestamp":"2026-10-20T19:30:02.040Z","level":"INFO","service":"ticketing-fulfillment-service","version":"1.2.0","environment":"qa","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"65be31d2a9f4078c","correlation_id":"9a8349ee-4fc3-42de-b2d2-791dfbb71c21","event":"ticket.email.sent","message":"Ticket fulfillment email sent after retry","duration_ms":181,"reservation_id":"79a22ea5-115d-4ec0-9564-4ea84c4fa592","ticket_id":"6b42f812-ae8e-4d86-b5fb-1c93c1f86b59","dependency":"smtp","delivery_attempt":2}
```

## Log level policy

| Level | Use when | Ticketing example |
|---|---|---|
| `ERROR` | An operation failed and requires retry, intervention, or investigation. | An SMTP attempt times out, or a message reaches the dead-letter queue after the configured retries. |
| `WARN` | Processing is deferred, retried, or safely skipped without immediate loss of the whole operation. | A contact projection is not yet available, so fulfillment remains pending and is retried; or a duplicate `eventId` is acknowledged without repeating side effects. |
| `INFO` | A significant business or lifecycle operation completes normally. | A new `BookingConfirmed` is processed and its ticket is persisted, or a ticket email is sent. |
| `DEBUG` | Temporary, non-sensitive detail is useful for diagnosing a specific flow and can be disabled in normal production operation. | Record which bounded fulfillment stage started or ended, using IDs and outcomes only, never the event payload, email, token, QR payload, or PDF contents. |

## Correlation and validation rules

- Inject and extract W3C trace context across HTTP and RabbitMQ message headers;
  keep the same `trace_id` through the asynchronous consumer when the context is
  available. If it is not available, create a new trace and retain correlation
  metadata rather than inventing a parent span.
- Use one `span_id` per operation, not one span ID for an entire trace.
- Keep `eventId` for Inbox idempotency and `correlationId` for business-event
  association; neither is interchangeable with `trace_id`.
- Validate that each emitted line parses as JSON, includes all required fields,
  uses UTC timestamps, and contains no prohibited sensitive values.
- The retry policy in the service documentation specifies three failed message
  attempts before the event is moved to
  `cine.ticketing_fulfillment.dlq`. Email fulfillment retry examples must be
  checked against the actual implementation and configuration before release.
