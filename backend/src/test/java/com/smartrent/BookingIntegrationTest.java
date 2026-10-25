package com.smartrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.Vehicle;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.dto.Dtos.BookingDto;
import com.smartrent.dto.Dtos.BookingRequest;
import com.smartrent.repo.PricingConfigRepository;
import com.smartrent.repo.VehicleRepository;
import com.smartrent.service.BookingService;
import com.smartrent.service.PricingService;
import com.smartrent.web.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BookingIntegrationTest {

    @Autowired BookingService bookings;
    @Autowired TestSupport support;
    @Autowired VehicleRepository vehicleRepo;
    @Autowired PricingService pricing;

    private static final LocalDate BASE = LocalDate.now().plusDays(200);

    private BookingRequest req(Vehicle v, int fromOffset, int toOffset) {
        return new BookingRequest(v.getId(), BASE.plusDays(fromOffset), BASE.plusDays(toOffset), null);
    }

    private static String code(Throwable t) {
        return ((ApiException) t).getCode();
    }

    @Test
    void createsABookingWithPersistedPriceAndConfirmedStatus() {
        Vehicle v = support.newVehicle();
        BookingDto b = bookings.create(support.customer(), req(v, 0, 3), null);
        assertThat(b.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.days()).isEqualTo(3);
        assertThat(b.totalPrice()).isPositive();
        assertThat(b.priceBreakdown()).isNotNull();
        assertThat(b.reference()).isNotBlank();
    }

    @Test
    void overlappingBookingIsRejectedWithConflict() {
        Vehicle v = support.newVehicle();
        bookings.create(support.customer(), req(v, 0, 5), null);
        assertThatThrownBy(() -> bookings.create(support.otherCustomer(), req(v, 3, 8), null))
                .isInstanceOf(ApiException.class).satisfies(e -> assertThat(code(e)).isEqualTo("BOOKING_CONFLICT"));
        // contained and containing ranges too
        assertThatThrownBy(() -> bookings.create(support.otherCustomer(), req(v, 1, 2), null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> bookings.create(support.otherCustomer(), req(v, -2, 9), null)).isInstanceOf(ApiException.class);
    }

    @Test
    void adjacentBookingsAreAllowedBecauseIntervalsAreHalfOpen() {
        Vehicle v = support.newVehicle();
        bookings.create(support.customer(), req(v, 0, 3), null);
        BookingDto after = bookings.create(support.otherCustomer(), req(v, 3, 5), null);   // pickup on the return day
        BookingDto before = bookings.create(support.otherCustomer(), req(v, -2, 0), null); // return on the pickup day
        assertThat(after.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(before.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void invalidDatesAreRejected() {
        Vehicle v = support.newVehicle();
        assertThatThrownBy(() -> bookings.create(support.customer(), req(v, 3, 3), null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_DATE_RANGE"));
        assertThatThrownBy(() -> bookings.create(support.customer(), req(v, 4, 2), null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> bookings.create(support.customer(),
                new BookingRequest(v.getId(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), null), null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void vehicleInMaintenanceCannotBeBooked() {
        Vehicle v = support.newVehicle();
        v.setStatus(VehicleStatus.MAINTENANCE);
        vehicleRepo.saveAndFlush(v);
        assertThatThrownBy(() -> bookings.create(support.customer(), req(v, 0, 2), null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("VEHICLE_UNAVAILABLE"));
    }

    @Test
    void cancellingFreesTheDates() {
        Vehicle v = support.newVehicle();
        BookingDto b = bookings.create(support.customer(), req(v, 0, 4), null);
        BookingDto cancelled = bookings.cancel(b.id(), support.customer(), "change of plans");
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookings.create(support.otherCustomer(), req(v, 0, 4), null).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancelTwiceAndForeignCancelAreRejected() {
        Vehicle v = support.newVehicle();
        BookingDto b = bookings.create(support.customer(), req(v, 0, 2), null);
        assertThatThrownBy(() -> bookings.cancel(b.id(), support.otherCustomer(), null)).isInstanceOf(ApiException.class);
        bookings.cancel(b.id(), support.customer(), null);
        assertThatThrownBy(() -> bookings.cancel(b.id(), support.customer(), null)).isInstanceOf(ApiException.class);
    }

    @Test
    void customerCannotReadAnotherCustomersBookingButStaffCan() {
        Vehicle v = support.newVehicle();
        BookingDto b = bookings.create(support.customer(), req(v, 0, 2), null);
        assertThatThrownBy(() -> bookings.get(b.id(), support.otherCustomer())).isInstanceOf(ApiException.class);
        assertThat(bookings.get(b.id(), support.customer()).id()).isEqualTo(b.id());
        assertThat(bookings.get(b.id(), support.employee()).id()).isEqualTo(b.id());
    }

    @Test
    void pickupAndReturnFollowTheStatusFlow() {
        Vehicle v = support.newVehicle();
        LocalDate today = LocalDate.now();
        BookingDto b = bookings.create(support.customer(), new BookingRequest(v.getId(), today, today.plusDays(2), null), null);
        assertThatThrownBy(() -> bookings.pickup(b.id(), support.customer())).isInstanceOf(ApiException.class); // not staff
        assertThatThrownBy(() -> bookings.returnVehicle(b.id(), support.employee())).isInstanceOf(ApiException.class); // not picked up
        assertThat(bookings.pickup(b.id(), support.employee()).status()).isEqualTo(BookingStatus.PICKED_UP);
        assertThat(bookings.returnVehicle(b.id(), support.employee()).status()).isEqualTo(BookingStatus.RETURNED);
        assertThat(bookings.timeline(b.id(), support.customer())).hasSize(3);
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameBookingAndDifferentRequestIsRejected() {
        Vehicle v = support.newVehicle();
        String key = "key-" + System.nanoTime();
        BookingDto first = bookings.create(support.customer(), req(v, 0, 2), key);
        BookingDto replay = bookings.create(support.customer(), req(v, 0, 2), key);
        assertThat(replay.id()).isEqualTo(first.id());
        assertThatThrownBy(() -> bookings.create(support.customer(), req(v, 10, 12), key)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void staleExpectedTotalIsRejectedAsPriceChanged() {
        Vehicle v = support.newVehicle();
        assertThatThrownBy(() -> bookings.create(support.customer(),
                new BookingRequest(v.getId(), BASE, BASE.plusDays(2), new BigDecimal("1.00")), null))
                .isInstanceOf(ApiException.class).satisfies(e -> assertThat(code(e)).isEqualTo("PRICE_CHANGED"));
    }

    @Test
    void confirmedPriceDoesNotChangeWhenPricingRulesChange() {
        Vehicle v = support.newVehicle();
        BookingDto b = bookings.create(support.customer(), req(v, 0, 3), null);
        var cfg = pricing.getConfigDto();
        pricing.updateConfig(new com.smartrent.dto.Dtos.PricingConfigDto(cfg.weekendSurcharge().add(new BigDecimal("0.20")),
                cfg.demandWindowDays(), cfg.demandBaseline(), cfg.demandWeight(), cfg.maxDemandSurcharge(), cfg.maxDemandDiscount(),
                cfg.availabilityWeight(), cfg.availabilityThreshold(), cfg.discount7Days(), cfg.discount14Days(), cfg.discount30Days(),
                cfg.minMultiplier(), cfg.maxMultiplier().add(new BigDecimal("1.0")), cfg.freeCancelDays(), cfg.lateCancelFeeRate(),
                null, null), "test");
        try {
            assertThat(bookings.get(b.id(), support.customer()).totalPrice()).isEqualByComparingTo(b.totalPrice());
        } finally {
            pricing.updateConfig(cfg, "test");
        }
    }

    /** Many simultaneous requests for the same vehicle and dates: exactly one may win. */
    @Test
    void concurrentRequestsForTheSameVehicleCreateExactlyOneBooking() throws Exception {
        Vehicle v = support.newVehicle();
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<String> unexpected = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        var customers = List.of(support.customer(), support.otherCustomer());
        for (int i = 0; i < threads; i++) {
            final var actor = customers.get(i % 2);
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    bookings.create(actor, req(v, 0, 3), null);
                    ok.incrementAndGet();
                } catch (ApiException e) {
                    if ("BOOKING_CONFLICT".equals(e.getCode())) conflicts.incrementAndGet(); else unexpected.add(e.getCode());
                } catch (Exception e) {
                    unexpected.add(e.toString());
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(unexpected).isEmpty();
        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
    }

    @Test
    void concurrentRequestsForDifferentVehiclesAllSucceed() throws Exception {
        List<Vehicle> vs = List.of(support.newVehicle(), support.newVehicle(), support.newVehicle(), support.newVehicle());
        ExecutorService pool = Executors.newFixedThreadPool(vs.size());
        List<Future<BookingDto>> fs = new ArrayList<>();
        for (Vehicle v : vs) fs.add(pool.submit(() -> bookings.create(support.customer(), req(v, 0, 2), null)));
        for (var f : fs) assertThat(f.get(60, TimeUnit.SECONDS).status()).isEqualTo(BookingStatus.CONFIRMED);
        pool.shutdown();
    }
}
