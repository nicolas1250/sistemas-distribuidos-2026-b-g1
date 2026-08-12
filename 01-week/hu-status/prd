# Cine API — Notification & Ticket Service

**Responsable:** Nicolas

## Introducción

Este documento describe el componente de **Notification & Ticket Service** del sistema **Cine API**, encargado de procesar de manera asíncrona las reservas confirmadas, generar los boletos digitales y enviar las notificaciones correspondientes a los usuarios.

El microservicio estará diseñado para trabajar de forma independiente de los demás servicios, utilizando un **broker de mensajería** para recibir los eventos generados cuando una reserva haya sido confirmada.

Su objetivo principal es garantizar que cada reserva confirmada genere automáticamente un boleto digital válido y que el usuario reciba la información de su compra mediante correo electrónico.

---

## 1. Notification & Ticket Service

**Tecnología:** Spring Boot  
**Responsable:** **Nicolas**

El servicio Notification & Ticket será responsable de la generación, almacenamiento y distribución de los boletos digitales asociados a las reservas.

Funciones principales:

- Consumo de eventos de reservas confirmadas.
- Generación de códigos QR para los boletos.
- Generación de boletos digitales en formato PDF.
- Envío de correos electrónicos de confirmación.
- Almacenamiento de los boletos generados.
- Consulta y descarga de boletos.
- Registro de las notificaciones enviadas.
- Reintento de notificaciones que hayan fallado.

---

## 2. Consumo de eventos

El microservicio funcionará mediante comunicación asíncrona con el servicio de reservas.

Cuando una reserva sea confirmada, **Booking & Seat Reservation Service** publicará un evento denominado:

```text
BookingConfirmed
```

El flujo será:

```text
Booking & Seat Reservation
          │
          │ BookingConfirmed
          ▼
   Message Broker
   RabbitMQ / Kafka
          │
          ▼
Notification & Ticket
          │
     ┌────┴────┐
     ▼         ▼
 Generar      Enviar
 QR/PDF       correo
```

El servicio deberá escuchar el evento y procesarlo sin necesidad de que el usuario permanezca conectado al sistema.

---

## 3. Generación del boleto digital

Una vez recibido el evento `BookingConfirmed`, el microservicio deberá generar un boleto digital asociado a la reserva.

El boleto deberá contener información como:

- Identificador de la reserva.
- Película.
- Fecha de la función.
- Hora de la función.
- Sala.
- Asientos reservados.
- Datos básicos del usuario.
- Código único del boleto.
- Código QR.

El código QR permitirá identificar y validar el boleto en los procesos posteriores de ingreso al cine.

El boleto será generado en formato:

```text
PDF
```

---

## 4. Generación del código QR

El sistema utilizará una librería especializada para generar el código QR.

El QR deberá contener información suficiente para identificar la reserva o el boleto sin exponer información sensible del usuario.

Ejemplo conceptual:

```text
Ticket
   │
   ├── Booking ID
   ├── Ticket ID
   └── Código de validación
          │
          ▼
       QR Code
```

La generación del QR será realizada automáticamente después de recibir el evento de confirmación de reserva.

---

## 5. Notificaciones por correo

Después de generar correctamente el boleto, el servicio enviará un correo electrónico al usuario.

El correo deberá incluir:

- Confirmación de la reserva.
- Información de la función.
- Información de los asientos.
- Boleto digital adjunto en PDF.
- Código QR correspondiente.

La comunicación podrá realizarse mediante:

- SMTP.
- SendGrid.

Flujo:

```text
BookingConfirmed
       │
       ▼
Generar PDF + QR
       │
       ▼
Enviar correo
       │
       ├── Éxito → Registrar notificación
       │
       └── Error → Registrar fallo
```

---

## 6. API Endpoints

El microservicio contará con los siguientes endpoints principales:

### Consultar boleto

```http
GET /api/v1/tickets/{booking_id}
```

Permite consultar y obtener el boleto digital asociado a una reserva.

### Reintentar notificación

```http
POST /api/v1/notifications/retry
```

Permite solicitar nuevamente el envío de una notificación que haya fallado.

### Consumidor de eventos

```text
BookingConfirmed
```

El consumidor estará conectado al broker de mensajería y procesará automáticamente las reservas confirmadas.

---

## 7. Base de Datos

El microservicio contará con una base de datos destinada al almacenamiento de información relacionada con los boletos y las notificaciones.

Se podrán manejar entidades como:

```text
Tickets
Notifications
```

### Tickets

Información principal:

- `id`
- `booking_id`
- `ticket_code`
- `qr_data`
- `pdf_path`
- `created_at`

### Notifications

Información principal:

- `id`
- `booking_id`
- `recipient`
- `type`
- `status`
- `attempts`
- `sent_at`
- `error_message`

El almacenamiento permitirá consultar los boletos generados y realizar seguimiento al estado de las notificaciones.

---

## 8. Manejo de errores y reintentos

El sistema deberá contemplar errores durante la generación del boleto o el envío del correo.

Los errores deberán registrarse para facilitar su seguimiento.

Ejemplo:

```text
Procesar BookingConfirmed
          │
          ▼
    Generar boleto
          │
     ┌────┴────┐
     │         │
   Éxito      Error
     │         │
     ▼         ▼
Enviar      Registrar
correo       error
     │
 ┌───┴────┐
 │        │
Éxito    Error
 │        │
 ▼        ▼
Finalizar Reintento
```

El sistema podrá realizar múltiples intentos de envío antes de marcar definitivamente la notificación como fallida.

---

## 9. Seguridad

El servicio deberá implementar mecanismos de seguridad para proteger la información relacionada con los usuarios y las reservas.

Se contemplan:

- Validación de eventos recibidos.
- Protección de endpoints.
- Validación del identificador de reserva.
- No exposición de información sensible en códigos QR.
- Gestión segura de credenciales SMTP o SendGrid.
- Uso de variables de entorno para secretos.
- Comunicación mediante HTTPS.
- Registro de eventos y errores.

---

## 10. Integración con los demás microservicios

El servicio tendrá principalmente comunicación con **Booking & Seat Reservation Service**.

```text
┌──────────────────────────────┐
│ Booking & Seat Reservation   │
└──────────────┬───────────────┘
               │
               │ BookingConfirmed
               ▼
       ┌───────────────┐
       │ RabbitMQ/Kafka│
       └───────┬───────┘
               │
               ▼
┌──────────────────────────────┐
│ Notification & Ticket       │
│ Service                     │
├──────────────────────────────┤
│ Generación QR               │
│ Generación PDF              │
│ Envío de correo             │
│ Gestión de tickets          │
│ Registro de notificaciones  │
└──────────────────────────────┘
```

Este enfoque permite mantener desacoplados los microservicios y evita que el proceso de generación y envío del boleto bloquee directamente la confirmación de una reserva.

---

## 11. Entregables de Nicolas

Como responsable del microservicio, Nicolas deberá entregar:

- Microservicio `Notification & Ticket Service`.
- Consumidor del evento `BookingConfirmed`.
- Integración con RabbitMQ o Kafka.
- Generación de códigos QR.
- Generación de boletos en PDF.
- Integración con SMTP o SendGrid.
- Endpoint `GET /api/v1/tickets/{booking_id}`.
- Endpoint `POST /api/v1/notifications/retry`.
- Base de datos para tickets y notificaciones.
- Manejo de errores y reintentos.
- Documentación mediante Swagger/OpenAPI.
- `Dockerfile` funcional.
- Configuración mediante variables de entorno.

---

## 12. Estructura propuesta del microservicio

```text
notification-ticket-service/
│
├── src/
│   ├── controller/
│   │   ├── TicketController
│   │   └── NotificationController
│   │
│   ├── service/
│   │   ├── TicketService
│   │   ├── NotificationService
│   │   ├── QrService
│   │   └── PdfService
│   │
│   ├── consumer/
│   │   └── BookingConfirmedConsumer
│   │
│   ├── model/
│   │   ├── Ticket
│   │   └── Notification
│   │
│   ├── repository/
│   │   ├── TicketRepository
│   │   └── NotificationRepository
│   │
│   └── config/
│       ├── BrokerConfig
│       └── MailConfig
│
├── Dockerfile
├── pom.xml
└── README.md
```

---

## 13. Flujo completo del servicio

```text
Usuario realiza reserva
          │
          ▼
Booking Service
          │
          ▼
Pago confirmado
          │
          ▼
Publica BookingConfirmed
          │
          ▼
RabbitMQ / Kafka
          │
          ▼
Notification & Ticket Service
          │
          ├── Generar QR
          │
          ├── Generar PDF
          │
          ├── Guardar Ticket
          │
          ├── Enviar correo
          │
          └── Registrar notificación
                    │
                    ▼
             Usuario recibe
              boleto digital
```

---

# Estándares comunes del equipo

Para garantizar la integración entre los cuatro microservicios, este módulo deberá cumplir los estándares definidos para todo el proyecto:

### Dockerización

El microservicio deberá incluir un `Dockerfile` funcional para permitir su ejecución mediante contenedores.

### Documentación

Los endpoints deberán estar documentados mediante:

```text
Swagger / OpenAPI
```

### Control de versiones

El desarrollo deberá realizarse mediante ramas:

```text
main
feature/notification-ticket
```

Los commits deberán utilizar mensajes descriptivos relacionados con los cambios realizados.

### Integración

El servicio deberá mantener contratos claros con los demás microservicios, especialmente con **Booking & Seat Reservation Service**, mediante el evento:

```text
BookingConfirmed
```

---

## Conclusión

El **Notification & Ticket Service** será el encargado de completar el proceso posterior a la confirmación de una reserva dentro de Cine API. Su arquitectura asíncrona permitirá generar los boletos digitales y enviar las notificaciones sin afectar directamente el funcionamiento del proceso de reservas.

Como responsable, **Nicolas** estará encargado de implementar la comunicación con el broker, generación de QR y PDF, envío de correos, almacenamiento de tickets, manejo de errores y exposición de los endpoints correspondientes.
