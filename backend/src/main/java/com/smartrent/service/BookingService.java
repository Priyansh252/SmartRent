package com.smartrent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrent.config.AppProperties;
import com.smartrent.domain.AppUser;
import com.smartrent.domain.Booking;
import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.BookingStatusHistory;
import com.smartrent.domain.EventType;
import com.smartrent.domain.Role;
import com.smartrent.domain.Vehicle;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.dto.Dtos.BookingDto;
import com.smartrent.dto.Dtos.BookingRequest;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.dto.Dtos.PriceBreakdown;
import com.smartrent.dto.Dtos.StaffDashboard;
import com.smartrent.dto.Dtos.TimelineEntry;
import com.smartrent.messaging.OutboxService;
import com.smartrent.repo.BookingRepository;
import com.smartrent.repo.BookingStatusHistoryRepository;
import com.smartrent.repo.UserRepository;
import com.smartrent.repo.VehicleRepository;
import com.smartrent.security.CurrentUser;
import com.smartrent.web.error.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-safe booking lifecycle.
 *
 * <p><b>Interval convention:</b> a rental occupies the half-open date interval [pickupDate, returnDate). A booking
 * that returns on the 15th does not block another that is picked up on the 15th.
 *
 * <p><b>Concurrency control:</b> {@link #create} runs in ONE transaction that first takes a pessimistic row lock
 * (SELECT ... FOR UPDATE) on the vehicle. Requests for the same vehicle are therefore serialized by the database:
 * the second request waits, then sees the first booking in its overlap check and is rejected with 409. READ_COMMITTED
 * isolation guarantees that check reads the latest committed data even on MySQL's default REPEATABLE READ.
 * Price, booking row, status history and outbox event are written atomically; any exception rolls everything back.
 */
@Service
public class BookingService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final BookingStatusHistoryRepository history;
    private final PricingService pricing;
    private final OutboxService outbox;
    private final ObjectMapper mapper;
    private final AppProperties props;
    private final Clock clock;

    public BookingService(BookingRepository bookings, VehicleRepository vehicles, UserRepository users,
                          BookingStatusHistoryRepository history, PricingService pricing, OutboxService outbox,
                          ObjectMapper mapper, AppProperties props, Clock clock) {
        this.bookings = bookings;
        this.vehicles = vehicles;
        this.users = users;
        this.history = history;
        this.pricing = pricing;
        this.outbox = outbox;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ rules shared with search / recommendations

    /** Validates a requested rental period; throws INVALID_DATE_RANGE (400) with a specific message. */
    public void validateRentalDates(LocalDate pickup, LocalDate returnDate) {
        LocalDate today = LocalDate.now(clock);
        if (pickup == null || returnDate == null) {
            throw ApiException.invalidDateRange("Both pickupDate and returnDate are required.");
        }
        if (pickup.isBefore(today)) {
            throw ApiException.invalidDateRange("pickupDate cannot be in the past.");
        }
        if (!returnDate.isAfter(pickup)) {
            throw ApiException.invalidDateRange("returnDate must be after pickupDate (minimum rental is 1 day).");
        }
        long days = ChronoUnit.DAYS.between(pickup, returnDate);
        if (days > props.getBooking().getMaxRentalDays()) {
            throw ApiException.invalidDateRange("A rental cannot exceed " + props.getBooking().getMaxRentalDays() + " days.");
        }
        if (pickup.isAfter(today.plusDays(props.getBooking().getMaxAdvanceDays()))) {
            throw ApiException.invalidDateRange("Bookings can be made at most " + props.getBooking().getMaxAdvanceDays()
                    + " days in advance.");
        }
    }

    // ------------------------------------------------------------------ create

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDto create(CurrentUser actor, BookingRequest req, String idempotencyKey) {
        validateRentalDates(req.pickupDate(), req.returnDate());
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 64) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be at most 64 characters.");
        }
        AppUser user = users.findById(actor.id()).orElseThrow(() -> ApiException.userNotFound(actor.id()));
        if (!user.isActive()) {
            throw ApiException.forbidden("This account is disabled.");
        }

        // 1) serialize all booking attempts for this vehicle
        Vehicle vehicle = vehicles.lockById(req.vehicleId()).orElseThrow(() -> ApiException.vehicleNotFound(req.vehicleId()));

        // 2) idempotent replay (checked after the lock so a concurrent duplicate sees the first request's booking)
        if (key != null) {
            var existing = bookings.findByUserIdAndIdempotencyKey(user.getId(), key);
            if (existing.isPresent()) {
                Booking b = existing.get();
                if (!b.getVehicle().getId().equals(vehicle.getId()) || !b.getPickupDate().equals(req.pickupDate())
                        || !b.getReturnDate().equals(req.returnDate())) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                            "This Idempotency-Key was already used for a different booking request.");
                }
                return Mappers.toDto(b, mapper);
            }
        }

        // 3) eligibility + availability on the locked vehicle
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw ApiException.vehicleUnavailable(vehicle.getId(),
                    vehicle.getStatus() == VehicleStatus.MAINTENANCE ? "it is under maintenance." : "it has been retired.");
        }
        long overlaps = bookings.countOverlapping(vehicle.getId(), req.pickupDate(), req.returnDate(), BookingStatus.BLOCKING);
        if (overlaps > 0) {
            throw ApiException.bookingConflict(vehicle.getId());
        }

        // 4) price is always computed on the server; a client-supplied expectedTotal is only a staleness check
        PriceBreakdown quote = pricing.quote(vehicle, req.pickupDate(), req.returnDate());
        if (req.expectedTotal() != null && req.expectedTotal().compareTo(quote.totalPrice()) != 0) {
            throw ApiException.priceChanged("The price for these dates is now " + quote.currency() + " "
                    + quote.totalPrice() + " (you expected " + req.expectedTotal() + "). Please review and confirm again.");
        }

        Booking b = new Booking();
        b.setReference(newReference());
        b.setUser(user);
        b.setVehicle(vehicle);
        b.setPickupDate(req.pickupDate());
        b.setReturnDate(req.returnDate());
        b.setStatus(BookingStatus.CONFIRMED);
        b.setDailyBaseRate(vehicle.getDailyRate());
        b.setTotalPrice(quote.totalPrice());
        b.setPriceBreakdown(toJson(quote));
        b.setIdempotencyKey(key);
        b.setCreatedAt(clock.instant());
        b = bookings.saveAndFlush(b);

        history.save(new BookingStatusHistory(b.getId(), null, BookingStatus.CONFIRMED, actor.email(), "Booking created"));
        outbox.record(EventType.BOOKING_CONFIRMED, b);
        return Mappers.toDto(b, mapper);
    }

    // ------------------------------------------------------------------ cancel / pickup / return

    @Transactional
    public BookingDto cancel(Long bookingId, CurrentUser actor, String reason) {
        Booking b = bookings.lockById(bookingId).orElseThrow(() -> ApiException.bookingNotFound(bookingId));
        requireOwnerOrAdmin(b, actor);
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.invalidState("Only CONFIRMED bookings can be cancelled; this booking is " + b.getStatus() + ".");
        }
        // Cancellation policy: free until freeCancelDays before pickup, then lateCancelFeeRate x total.
        var cfg = pricing.currentConfig();
        long daysToPickup = ChronoUnit.DAYS.between(LocalDate.now(clock), b.getPickupDate());
        BigDecimal fee = daysToPickup < cfg.getFreeCancelDays()
                ? b.getTotalPrice().multiply(cfg.getLateCancelFeeRate()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        b.setStatus(BookingStatus.CANCELLED);
        b.setCancelledAt(clock.instant());
        b.setCancellationFee(fee);
        String note = (reason == null || reason.isBlank() ? "Cancelled" : "Cancelled: " + reason.trim())
                + (fee.signum() > 0 ? " (late-cancellation fee " + props.getCurrency() + " " + fee + ")" : " (no fee)");
        history.save(new BookingStatusHistory(b.getId(), BookingStatus.CONFIRMED, BookingStatus.CANCELLED, actor.email(), note));
        outbox.record(EventType.BOOKING_CANCELLED, b);
        return Mappers.toDto(b, mapper);
    }

    @Transactional
    public BookingDto pickup(Long bookingId, CurrentUser actor) {
        requireStaff(actor);
        Booking b = bookings.lockById(bookingId).orElseThrow(() -> ApiException.bookingNotFound(bookingId));
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.invalidState("Only CONFIRMED bookings can be picked up; this booking is " + b.getStatus() + ".");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate earliest = b.getPickupDate().minusDays(props.getBooking().getPickupDaysBeforeAllowed());
        if (today.isBefore(earliest)) {
            throw ApiException.invalidState("Pickup is only possible from " + earliest + ".");
        }
        if (!today.isBefore(b.getReturnDate())) {
            throw ApiException.invalidState("The rental period ended on " + b.getReturnDate() + "; the booking can no longer be picked up.");
        }
        if (b.getVehicle().getStatus() != VehicleStatus.AVAILABLE) {
            throw ApiException.vehicleUnavailable(b.getVehicle().getId(), "its status is " + b.getVehicle().getStatus() + ".");
        }
        b.setStatus(BookingStatus.PICKED_UP);
        b.setPickedUpAt(clock.instant());
        history.save(new BookingStatusHistory(b.getId(), BookingStatus.CONFIRMED, BookingStatus.PICKED_UP, actor.email(),
                "Vehicle handed over"));
        outbox.record(EventType.VEHICLE_PICKED_UP, b);
        return Mappers.toDto(b, mapper);
    }

    @Transactional
    public BookingDto returnVehicle(Long bookingId, CurrentUser actor) {
        requireStaff(actor);
        Booking b = bookings.lockById(bookingId).orElseThrow(() -> ApiException.bookingNotFound(bookingId));
        if (b.getStatus() != BookingStatus.PICKED_UP) {
            throw ApiException.invalidState("Only PICKED_UP bookings can be returned; this booking is " + b.getStatus() + ".");
        }
        b.setStatus(BookingStatus.RETURNED);
        b.setReturnedAt(clock.instant());
        history.save(new BookingStatusHistory(b.getId(), BookingStatus.PICKED_UP, BookingStatus.RETURNED, actor.email(),
                "Vehicle returned"));
        outbox.record(EventType.VEHICLE_RETURNED, b);
        return Mappers.toDto(b, mapper);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public BookingDto get(Long id, CurrentUser actor) {
        Booking b = bookings.findWithDetailsById(id).orElseThrow(() -> ApiException.bookingNotFound(id));
        requireCanView(b, actor);
        return Mappers.toDto(b, mapper);
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(Long id, CurrentUser actor) {
        Booking b = bookings.findWithDetailsById(id).orElseThrow(() -> ApiException.bookingNotFound(id));
        requireCanView(b, actor);
        return history.findByBookingIdOrderByChangedAtAscIdAsc(id).stream().map(Mappers::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingDto> mine(Long userId, int page, int size) {
        Page<Booking> p = bookings.findByUserId(userId, pageable(page, size));
        return PageResponse.of(p.map(b -> Mappers.toDto(b, mapper)));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingDto> list(BookingStatus status, int page, int size) {
        Page<Booking> p = status == null ? bookings.findAllBy(pageable(page, size))
                : bookings.findByStatus(status, pageable(page, size));
        return PageResponse.of(p.map(b -> Mappers.toDto(b, mapper)));
    }

    @Transactional(readOnly = true)
    public StaffDashboard staffDashboard() {
        LocalDate today = LocalDate.now(clock);
        List<BookingDto> due = bookings.findByStatusAndPickupDateLessThanEqualOrderByPickupDateAsc(BookingStatus.CONFIRMED, today)
                .stream().map(b -> Mappers.toDto(b, mapper)).toList();
        List<BookingDto> active = bookings.findByStatusOrderByReturnDateAsc(BookingStatus.PICKED_UP)
                .stream().map(b -> Mappers.toDto(b, mapper)).toList();
        return new StaffDashboard(due, active, due.size(), active.size());
    }

    // ------------------------------------------------------------------ helpers

    private static void requireStaff(CurrentUser actor) {
        if (!actor.isStaff()) {
            throw ApiException.forbidden("Only employees and administrators can record pickups and returns.");
        }
    }

    private static void requireOwnerOrAdmin(Booking b, CurrentUser actor) {
        boolean owner = b.getUser().getId().equals(actor.id());
        if (!(owner && actor.role() == Role.CUSTOMER) && !actor.isAdmin()) {
            throw ApiException.forbidden("You can only cancel your own bookings.");
        }
    }

    private static void requireCanView(Booking b, CurrentUser actor) {
        if (!actor.isStaff() && !b.getUser().getId().equals(actor.id())) {
            throw ApiException.forbidden("This booking belongs to another customer.");
        }
    }

    private static org.springframework.data.domain.Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
    }

    private String toJson(PriceBreakdown quote) {
        try {
            return mapper.writeValueAsString(quote);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize price breakdown", e);
        }
    }

    private static String newReference() {
        StringBuilder sb = new StringBuilder("SR-");
        for (int i = 0; i < 8; i++) {
            sb.append(REF_ALPHABET.charAt(RANDOM.nextInt(REF_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Exposed for tests that need "now" consistent with the service. */
    public Instant now() {
        return clock.instant();
    }
}
