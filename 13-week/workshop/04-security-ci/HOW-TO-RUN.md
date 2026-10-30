# How to run the negative authorization test

Covers: story "[security] csp-ticketing-api accepts only tokens signed by csp-auth-api" (threat S), acceptance criteria 1 and 2.

1. Start `ticketing-fulfillment-service` on port 8084 (`docker compose up -d ticketing-fulfillment-service`, see `csp-docs/09-microservices/services/05-ticketing-fulfillment-service/README.md`).
2. From this folder run:

```bash
java TicketingAuthzNegativeTest.java
```

Use `BASE_URL` to point at another host. The test sends `GET /api/v1/ticketing/me/tickets` without a token and with an `alg=none` token; both must answer `401`. It exits with code 1 if any answer is different.

## Real output (CP8)

Run on 2026-10-30 against the real service built from `develop` of `csp-ticketing-api` (commit `40afbb3`, includes `csp-ticketing-api#36`). Setup, all local and disposable: PostgreSQL 16 and RabbitMQ 3.13 in Docker, the 9 migrations of `csp-ticketing-db` (`develop`, `082e693`) applied with Flyway, a test RSA key as `JWT_PUBLIC_KEY_FILE`, and `mvn spring-boot:run` on the `app` module (port 8084).

```text
PASS GET /me/tickets without Authorization -> HTTP 401 (expected 401)
PASS GET /me/tickets with an alg=none token -> HTTP 401 (expected 401)
RESULT: PASS
```

The test passes because `develop` now validates the token (`SecurityConfiguration`). It was written against the state of 2026-10-28, when the service did not validate the JWT yet; I did not run it against that older state. The same service answers `401` to `POST /internal/tickets/{ticketId}/pdf` without a token (`curl`, HTTP 401), which is the first part of criterion 1 of #293.

## security.yml validation (CP8)

Checked with `rhysd/actionlint:1.7.7` (Docker) on 2026-10-30:

```text
actionlint security.yml  -> no output, exit code 0
```
