# SmartRent: Test Report

## Summary

| Item | Status |
|---|---|
| Maven compile (78 main + 6 test source files) | Executed, passed |
| Automated tests (`mvn clean verify`) | **Executed: 42 run, 0 failures, 0 errors, 0 skipped** |
| Build result | **BUILD SUCCESS** (25 Oct 2026 run, Windows, Java 17.0.20, Maven) |
| Application start-up (H2 profile, embedded Artemis) | Executed, passed |
| Browser check and screenshots | Executed (price breakdown, overlap refusal, notification history, report history, Swagger UI) |
| MySQL with external Artemis broker (Docker) | **Not tested** |
| Load or performance testing | **Not done** |

## Results by test class

| Class | Type | Tests | Result |
|---|---|---|---|
| `PricingEngineTest` | unit | 9 | all passed |
| `BookingIntegrationTest` | integration (H2) | 14 | all passed |
| `SearchAndRecommendationTest` | integration | 6 | all passed |
| `SecurityApiTest` | MockMvc | 9 | all passed |
| `MessagingAndBatchTest` | integration (embedded Artemis) | 4 | all passed |
| **Total** | | **42** | **42 passed** |

## What the tests cover

- **Pricing:** weekday and weekend, peak periods, duration discount, scarcity, clamping, price lines sum to the total.
- **Booking:** create, overlap conflicts, half-open date edges, invalid dates, maintenance vehicles, cancellation frees dates, ownership checks, idempotency, price-changed check, and a 12-thread race for one vehicle (exactly one request succeeds).
- **Search and recommendations:** Criteria API filters, availability inside the query, sorting whitelist, pagination totals, ranked recommendations with reasons.
- **Security:** public and protected endpoints, JSON 401 and 403, role matrix, customer isolation, CSRF rejection, structured error responses.
- **Messaging and batch:** outbox to JMS to notification, duplicate event ignored, malformed message dropped, all five report jobs write a CSV file whose row count matches the run record.

## Note on one early failure

The first full run had 41 passing and 1 error: the report test asked for a 430-day range, while the application correctly limits reports to 366 days. The test date range was corrected (to 300 days ahead) and the full suite was re-run; all 42 tests then passed.

## How to run

```bash
cd backend
mvn clean verify
# reports: backend/target/surefire-reports/
```

## Limits of this report

The tests run on H2 with an embedded Artemis broker. They do not prove behaviour on MySQL with an external broker, under heavy load, or with several application instances.
