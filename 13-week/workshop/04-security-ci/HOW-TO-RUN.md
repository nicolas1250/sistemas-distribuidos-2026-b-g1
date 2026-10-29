# How to run the negative authorization test

Covers: story "[security] csp-ticketing-api accepts only tokens signed by csp-auth-api" (threat S), acceptance criteria 1 and 2.

1. Start `ticketing-fulfillment-service` on port 8084 (`docker compose up -d ticketing-fulfillment-service`, see `csp-docs/09-microservices/services/05-ticketing-fulfillment-service/README.md`).
2. From this folder run:

```bash
java TicketingAuthzNegativeTest.java
```

Use `BASE_URL` to point at another host. The test sends `GET /api/v1/ticketing/me/tickets` without a token and with an `alg=none` token; both must answer `401`. It exits with code 1 if any answer is different.

## Real output (CP8)

<paste the console output here in CP8>

## security.yml validation (CP8)

<paste the actionlint result here in CP8>
