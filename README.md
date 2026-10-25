# SmartRent: Smart Car Rental and Fleet Management System


Author: Priyansh Mudela

SmartRent is a Spring Boot web application for a car rental company. Customers search by real availability, see an itemised price and book. Employees record pickups and returns. Admins manage the fleet, pricing and reports.

## Key features

- **Transaction-safe booking:** the vehicle row is locked, dates use a half-open interval `[pickup, return)`, and idempotency keys make retries safe.
- **Criteria API search:** filters, sorting, paging and an availability sub-query in one database query.
- **Explainable dynamic pricing:** weekend, peak, demand, scarcity and duration rules. Every line is shown and the agreed price is saved with the booking.
- **Recommendations:** ranked cars with score parts and plain-language reasons (read-only).
- **Asynchronous notifications:** transactional outbox, Spring JMS, ActiveMQ Artemis and an idempotent listener.
- **Automated reports:** five Spring Batch jobs write CSV files, run on demand or nightly at 02:00, with history and download.
- **Security:** Spring Security with three roles (customer, employee, admin), BCrypt, CSRF and JSON 401/403 responses.

## Tech stack

| Area | Technology |
|---|---|
| Language and build | Java 17, Maven |
| Framework | Spring Boot 3.2.5 |
| Web | Spring MVC, Thymeleaf, JavaScript |
| Data | Spring Data JPA, Hibernate 6, Criteria API, Flyway |
| Database | MySQL 8 (main), H2 (demo and tests) |
| Messaging | Spring JMS, Apache ActiveMQ Artemis |
| Batch | Spring Batch 5 |
| API docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Spring Boot Test, MockMvc, Awaitility |

## Quick start (no Docker, uses H2 and an embedded Artemis broker)

Requirements: Java 17 or newer and Maven 3.9 or newer.

```bash
git clone https://github.com/Priyansh252/SmartRent
cd SmartRent/backend
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```

Open http://localhost:8080 when the console shows `Started SmartRentApplication`. Demo data is created on first start.

## Run with MySQL and Artemis (Docker)

```bash
cp .env.example .env        # then edit the change_me_* passwords; never commit .env
docker compose up -d        # MySQL on 3306, Artemis on 61616 (console 8161)
cd backend
mvn spring-boot:run
```

## Run the tests

```bash
cd backend
mvn clean verify
```

The tests use H2 and an embedded Artemis broker, so Docker is not needed.

## Useful URLs

| What | URL |
|---|---|
| Application | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health check | http://localhost:8080/actuator/health |

## Demo accounts (local demo data only)

| Role | E-mail | Password |
|---|---|---|
| Admin | admin@smartrent.demo | Admin@123 |
| Employee | employee@smartrent.demo | Employee@123 |
| Customer | customer@smartrent.demo, arjun.mehta@smartrent.demo | Customer@123 |

These are default passwords for local demos only. Set `SEED_ADMIN_PASSWORD`, `SEED_EMPLOYEE_PASSWORD` and `SEED_CUSTOMER_PASSWORD` to change them. Do not use them on a public server.

## Project layout

```
backend/     Spring Boot application (domain, repo, service, web, security, messaging, batch, seed)
frontend/    Thymeleaf templates, CSS, JavaScript and images
scripts/     helper scripts
docker-compose.yml   MySQL and Artemis for local use
.env.example         template for local settings (copy to .env)
```

## Test status

`mvn clean verify` on the H2 profile: **42 tests run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS.**
Not tested yet: MySQL with an external Artemis broker, and load or performance testing.

## Known limitations

- The outbox relay assumes a single application instance.
- Notifications are stored and shown in the admin page; e-mail or SMS is not sent.
- Employees are not assigned to individual bookings.

## Configuration and secrets

No real passwords or keys are stored in this repository. Database, broker and demo-account passwords are read from environment variables or a local `.env` file, which is ignored by git.

## Licence

Academic project for coursework. No licence is granted for commercial use.
