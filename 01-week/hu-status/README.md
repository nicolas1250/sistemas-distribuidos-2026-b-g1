<!-- HU-STATUS TEMPLATE - do NOT remove the <!-- ... --> markers or the table headers.
     Your weekly grade is read AUTOMATICALLY from this file:
       01-week/hu-status/README.md  (inside YOUR fork). English. -->

# Weekly Status - Week 01

<!-- CONFIG-START - must match your profile repo (username/username) CONFIG -->
- FULL_NAME: Nicolas Obregon Rojas
- GITHUB_USER: nicolas1250
- TEAM: CineSync Platform
- SPRINT_GOAL: Design and define the Notification & Ticket microservice, responsible for processing confirmed bookings, generating digital tickets with QR/PDF, and sending the corresponding notifications to users.
<!-- CONFIG-END -->

## 1. User stories worked this week

| HU ID | Title | Status (todo/doing/done) | Evidence (PR or commit URL) |
|---|---|---|---|
| HU-CINE-001 | Generate a digital ticket from a confirmed booking | doing | Pending evidence |
| HU-CINE-002 | Generate a QR code associated with the digital ticket | doing | Pending evidence |
| HU-CINE-003 | Send the generated ticket to the user by email | todo | Pending - implementation pending |
| HU-CINE-004 | Allow users to view and download a ticket using the booking ID | todo | Pending - endpoint not implemented yet |
| HU-CINE-005 | Register and retry failed notifications | todo | Pending - functionality not implemented yet |

## 2. My individual contribution

- Defined the scope of the **Notification & Ticket Service**, responsible for the processing that takes place after a booking is confirmed.
- Documented the communication flow between the **Booking & Seat Reservation Service**, the message broker, and the notification service.
- Defined the `BookingConfirmed` event as the asynchronous communication mechanism used to start the ticket generation process.
- Established the main responsibilities of the service: event consumption, QR generation, PDF generation, email delivery, and ticket and notification storage.
- Defined the main API endpoints for the microservice:
  - `GET /api/v1/tickets/{booking_id}`
  - `POST /api/v1/notifications/retry`
- Proposed the initial microservice structure, separating controllers, services, event consumers, models, repositories, and configuration.
- Defined the main persistence entities: `Tickets` and `Notifications`.
- Documented the ticket generation flow: receiving `BookingConfirmed`, generating the QR code, generating the PDF, storing the ticket, and sending the email.
- Considered error handling and retry mechanisms for notifications that cannot be delivered successfully.
- Defined the use of environment variables to protect credentials and configuration related to SMTP or SendGrid.
- Included Dockerization and Swagger/OpenAPI documentation as part of the team's common technical standards.

## 3. Blockers and risks

- The final integration with the **Booking & Seat Reservation Service** depends on defining and confirming the contract for the `BookingConfirmed` event.
- The project still needs to decide whether **RabbitMQ or Kafka** will be used as the message broker.
- The email service integration, either SMTP or SendGrid, still needs to be configured and tested.
- The appropriate libraries for PDF and QR code generation still need to be selected and configured.
- The final storage solution for generated PDF files needs to be defined, particularly whether local storage or an external storage service will be used.
- There is currently no evidence of unit or integration tests for the microservice functionality.
- The service endpoints and data models must be validated against the contracts defined by the other team members.

## 4. Plan for next week

- Create the initial structure of the `notification-ticket-service` microservice.
- Implement the `BookingConfirmed` event consumer.
- Define and implement the `Ticket` and `Notification` models.
- Implement QR code generation.
- Implement digital ticket generation in PDF format.
- Implement email delivery using SMTP or SendGrid.
- Create the `GET /api/v1/tickets/{booking_id}` endpoint.
- Create the `POST /api/v1/notifications/retry` endpoint.
- Implement notification logging and the retry mechanism.
- Add unit tests for ticket and QR code generation.
- Add integration tests for processing the `BookingConfirmed` event.
- Create the `Dockerfile` and verify the microservice using Docker.
- Document the API endpoints using Swagger/OpenAPI.
- Coordinate with the Booking & Seat Reservation Service owner to finalize the `BookingConfirmed` event contract.

## 5. Compliance self-check

- [ ] Conventional Commits - `type(scope): summary`
- [ ] Per-environment HU branch + PR to that environment (`hu-xxx-dev -> develop`, ...)
- [ ] Testable acceptance criteria
- [ ] Tests added/updated (unit / integration)
- [ ] DDD / hexagonal boundaries respected (domain has no I/O)
- [x] No secrets; config via environment variables

Notes:

- The user stories are mainly in the design and definition stage during this first week.
- The broker integration still depends on the team's decision between RabbitMQ and Kafka.
- Tests will be added during the implementation of the microservice.
- Email credentials and other sensitive information must be managed through environment variables.
- The separation between domain, application, and infrastructure layers will be maintained to prevent business logic from depending directly on I/O components.

## 6. Evidence links

- Notification & Ticket Service PDR: `PDR-Notification-Ticket.md`
- Repository: `https://github.com/nicolas1250`
- Implementation evidence: Pending
- Pull Request evidence: Pending
- Test evidence: Pending
