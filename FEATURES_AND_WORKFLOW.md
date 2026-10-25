# SmartRent - Features and Workflow

## Roles
- **CUSTOMER**: search, quotes, recommendations, book, view own bookings and timeline, cancel, see own notifications.
- **EMPLOYEE**: operations dashboard, pickup and return, vehicle status, maintenance records.
- **ADMIN**: everything above plus vehicle CRUD, users, pricing rules and peak periods, all bookings, notifications/outbox, audit log, reports, CSV exports, charts.

Session login (form) with BCrypt; cookie-based CSRF (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header); REST returns JSON 401/403. Customers get 403 on other customers' bookings.

## Booking (transaction-safe)
1. `POST /api/bookings` (customer, optional `Idempotency-Key` header, optional `expectedTotal`).
2. `BookingService.create` runs in a READ_COMMITTED transaction, takes a `PESSIMISTIC_WRITE` lock on the **vehicle row**, then checks: idempotency replay, vehicle status, overlapping bookings (`pickup < other.return AND return > other.pickup`, i.e. half-open `[pickup, return)`), and the server-side price. If `expectedTotal` differs from the fresh price the request fails with `PRICE_CHANGED`.
3. The booking, its status history row and an outbox event are written in the same transaction; the agreed price and breakdown are stored on the booking.
4. Concurrent attempts on one vehicle serialize on the lock; the loser gets HTTP 409 `BOOKING_CONFLICT`. Back-to-back rentals (return day = next pickup day) are allowed.
5. Status flow: `CONFIRMED -> PICKED_UP -> RETURNED`, or `CONFIRMED -> CANCELLED`. Cancellation is free until `freeCancelDays` before pickup, then a configurable fee applies.

## Search (Criteria API)
`VehicleSearchRepository` builds a `CriteriaQuery` from optional filters (price range, type, seats, location, fuel, transmission) and, when dates are given, a correlated `NOT EXISTS` subquery on bookings so availability is decided in the database. Sorting uses a whitelist (`price, seats, year, make, mileage`); a separate count query drives pagination.

## Dynamic pricing
`PricingEngine` is a pure function. `multiplier = 1 + weekend + peak + demand + availability - durationDiscount`, clamped to `[min, max]`. Each rule is a visible line in the breakdown; a LIMIT/ROUNDING line makes lines sum to the total. All weights, discounts, limits and peak periods are editable by the admin and apply only to new quotes.

## Recommendations
`POST /api/recommendations` filters on mandatory requirements (free for the dates, seats, type, budget, same-location when requested), then scores candidates by weighted components (price fit, capacity fit, location distance via haversine, preferences, availability scarcity). Output: rank, score, component scores, reasons and the price breakdown. It never creates a booking.

## JMS notifications
Booking/cancel/pickup/return write an `OutboxEvent` in the booking transaction (`OutboxService`, mandatory propagation). `OutboxRelay` (scheduled) publishes PENDING events as JSON to queue `smartrent.booking.events` on Artemis and marks them SENT (at-least-once). `BookingEventListener` (`@JmsListener`) calls `NotificationService.handle`, which is idempotent by `eventId` (existence check + unique constraint). Malformed messages are dropped and logged; other failures roll back and are redelivered. History: customer `GET /api/notifications/mine`, admin `GET /api/admin/notifications` and `/api/admin/outbox`.

## Spring Batch reports
Five jobs (revenue summary, booking counts, vehicle usage, fleet utilization, fleet availability): JDBC cursor reader -> processor -> CSV `FlatFileItemWriter` into `REPORTS_DIR`. Triggered by `POST /api/admin/reports`, nightly by `ReportScheduler` (cron in config), tracked in `report_runs`, downloadable via `GET /api/admin/reports/{id}/download`. The range is `[from, to)`.

## Errors
`@RestControllerAdvice` returns `{timestamp,status,error,code,message,path,fieldErrors}` for validation (400), invalid dates (400), not found (404), forbidden (403), unauthenticated (401), conflicts/constraints (409) and unexpected errors (500, generic message, no stack trace).

## Extras
Booking timeline, maintenance records, audit log, revenue/booking charts, CSV exports, Swagger UI, actuator health, seeded data.

## Screens
Landing + search, vehicle list with filters, vehicle detail with live price breakdown and booking, login/register, booking confirmation and detail with timeline, recommendations, customer dashboard, employee operations, admin fleet/bookings/users/events/audit, pricing configuration, report history.
