# SmartRent - Demo Guide

Start the app first (see README). Everything below is a suggested script; it has **not** been run by the author.

## UI walkthrough (about 10 minutes)
1. Open http://localhost:8080. The headline numbers and featured cars come from the database.
2. Search with dates and a type, open a vehicle, change the dates and watch the price breakdown (weekend, peak, demand, duration) update.
3. Sign in as `customer@smartrent.demo` / `Customer@123` and book. You land on the booking page with the locked price and a timeline.
4. Open **Find my car**, enter 5 seats and a budget; read the match reasons.
5. In a second browser (or private window) sign in as `admin@smartrent.demo` / `Admin@123`. Show **Admin -> Notifications & JMS**: the confirmation notification arrived through Artemis.
6. Try booking the same vehicle and overlapping dates as another customer: the API answers 409 and the UI shows the message. Booking starting on the previous return date succeeds.
7. As `employee@smartrent.demo` / `Employee@123` open **Operations**: pickup (allowed from the pickup date) then return.
8. Admin -> **Pricing**: raise the weekend surcharge, add a peak period, use "Try the rules". Existing bookings keep their price.
9. Admin -> **Reports**: generate a Revenue Summary and download the CSV; the status comes from Spring Batch.
10. Swagger UI: http://localhost:8080/swagger-ui.html.

## curl walkthrough (session + CSRF)
```bash
B=http://localhost:8080; J=$(mktemp)
tok() { awk '$6=="XSRF-TOKEN"{print $7}' "$J"; }
curl -s -c "$J" -b "$J" -o /dev/null $B/login
curl -s -c "$J" -b "$J" -o /dev/null -w "login: %{http_code}\n" \
  --data-urlencode "email=customer@smartrent.demo" --data-urlencode "password=Customer@123" --data-urlencode "_csrf=$(tok)" $B/login
curl -s -c "$J" -b "$J" -o /dev/null $B/account          # refreshes the CSRF cookie

# availability + quote
curl -s "$B/api/vehicles?pickupDate=2030-03-04&returnDate=2030-03-07&type=SUV&sort=price" | head -c 600; echo
curl -s "$B/api/vehicles/1/quote?pickupDate=2030-03-04&returnDate=2030-03-07"; echo

# book (idempotent) - repeat the same command: same booking returned
curl -s -c "$J" -b "$J" -X POST $B/api/bookings -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $(tok)" -H "Idempotency-Key: demo-1" \
  -d '{"vehicleId":1,"pickupDate":"2030-03-04","returnDate":"2030-03-07"}'; echo

# overlapping request (as the same or another customer) -> 409 BOOKING_CONFLICT
curl -s -b "$J" -X POST $B/api/bookings -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok)" \
  -d '{"vehicleId":1,"pickupDate":"2030-03-05","returnDate":"2030-03-06"}'; echo

curl -s -b "$J" $B/api/bookings/mine; echo
curl -s -b "$J" $B/api/notifications/mine; echo
curl -s -b "$J" -o /dev/null -w "admin endpoint as customer: %{http_code}\n" $B/api/admin/dashboard   # 403
```
Vehicle id 1 exists in the seed data; if a date range is taken pick another.

## Concurrency demo
Run the booking `curl` for the same vehicle and dates from several terminals at once (or run `BookingIntegrationTest#concurrentRequestsForTheSameVehicleCreateExactlyOneBooking`): exactly one returns 201, the others 409.

## Broker check
With Docker: open http://localhost:8161, queue `smartrent.booking.events`; messages are consumed immediately, so look at message counts or the admin Notifications tab. Stop Artemis, book, and observe the outbox row stay PENDING; start Artemis and it is delivered.
