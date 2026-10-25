package com.smartrent.service;

import com.smartrent.config.AppProperties;
import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.FuelType;
import com.smartrent.domain.Location;
import com.smartrent.domain.MaintenanceRecord;
import com.smartrent.domain.Transmission;
import com.smartrent.domain.Vehicle;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.domain.VehicleType;
import com.smartrent.dto.Dtos.BookedRange;
import com.smartrent.dto.Dtos.MaintenanceDto;
import com.smartrent.dto.Dtos.MaintenanceRequest;
import com.smartrent.dto.Dtos.MetaDto;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.dto.Dtos.PriceBreakdown;
import com.smartrent.dto.Dtos.QuoteResponse;
import com.smartrent.dto.Dtos.VehicleDto;
import com.smartrent.dto.Dtos.VehicleRequest;
import com.smartrent.dto.Dtos.VehicleSearchItem;
import com.smartrent.repo.BookingRepository;
import com.smartrent.repo.LocationRepository;
import com.smartrent.repo.MaintenanceRecordRepository;
import com.smartrent.repo.VehicleRepository;
import com.smartrent.repo.VehicleSearchCriteria;
import com.smartrent.repo.VehicleSearchRepository;
import com.smartrent.security.CurrentUser;
import com.smartrent.web.error.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private final VehicleRepository vehicles;
    private final LocationRepository locations;
    private final BookingRepository bookings;
    private final MaintenanceRecordRepository maintenance;
    private final VehicleSearchRepository search;
    private final PricingService pricing;
    private final BookingService bookingRules;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, LocationRepository locations, BookingRepository bookings,
                          MaintenanceRecordRepository maintenance, VehicleSearchRepository search,
                          PricingService pricing, BookingService bookingRules, AuditService audit,
                          AppProperties props, Clock clock) {
        this.vehicles = vehicles;
        this.locations = locations;
        this.bookings = bookings;
        this.maintenance = maintenance;
        this.search = search;
        this.pricing = pricing;
        this.bookingRules = bookingRules;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ search / read

    /**
     * Public/customer search: only bookable (AVAILABLE) vehicles. When dates are given the database also excludes
     * vehicles with a conflicting booking; price estimates are then computed for the returned page only.
     */
    @Transactional(readOnly = true)
    public PageResponse<VehicleSearchItem> searchAvailable(BigDecimal minPrice, BigDecimal maxPrice, LocalDate pickup,
                                                           LocalDate returnDate, VehicleType type, Integer minSeats,
                                                           Long locationId, FuelType fuel, Transmission transmission,
                                                           String sort, String direction, int page, int size) {
        validateFilters(minPrice, maxPrice, pickup, returnDate, minSeats, page, size);
        if (pickup != null) {
            bookingRules.validateRentalDates(pickup, returnDate);
        }
        VehicleSearchCriteria c = new VehicleSearchCriteria(minPrice, maxPrice, pickup, returnDate, type, minSeats,
                locationId, fuel, transmission, VehicleStatus.AVAILABLE);
        Page<Vehicle> result = search.search(c, page, size, sort, !"desc".equalsIgnoreCase(direction));
        return PageResponse.of(result.map(v -> {
            VehicleDto dto = Mappers.toDto(v);
            if (c.hasDates()) {
                PriceBreakdown q = pricing.quote(v, pickup, returnDate);
                return new VehicleSearchItem(dto, q.totalPrice(), q.averageDailyPrice());
            }
            return new VehicleSearchItem(dto, null, null);
        }));
    }

    /** Staff/admin fleet list: every status (optionally filtered), no availability window. */
    @Transactional(readOnly = true)
    public PageResponse<VehicleDto> listFleet(VehicleStatus status, VehicleType type, Long locationId, String sort,
                                              String direction, int page, int size) {
        validateFilters(null, null, null, null, null, page, size);
        VehicleSearchCriteria c = new VehicleSearchCriteria(null, null, null, null, type, null, locationId, null, null, status);
        return PageResponse.of(search.search(c, page, size, sort, !"desc".equalsIgnoreCase(direction)).map(Mappers::toDto));
    }

    @Transactional(readOnly = true)
    public VehicleDto get(Long id) {
        Vehicle v = vehicles.findWithLocation(id).orElseThrow(() -> ApiException.vehicleNotFound(id));
        if (v.getStatus() == VehicleStatus.RETIRED) {
            throw ApiException.vehicleNotFound(id);
        }
        return Mappers.toDto(v);
    }

    @Transactional(readOnly = true)
    public QuoteResponse quote(Long id, LocalDate pickup, LocalDate returnDate) {
        bookingRules.validateRentalDates(pickup, returnDate);
        Vehicle v = vehicles.findWithLocation(id).orElseThrow(() -> ApiException.vehicleNotFound(id));
        PriceBreakdown price = pricing.quote(v, pickup, returnDate);
        boolean free = v.getStatus() == VehicleStatus.AVAILABLE
                && bookings.countOverlapping(id, pickup, returnDate, BookingStatus.BLOCKING) == 0;
        String message = free ? "Available for these dates."
                : v.getStatus() != VehicleStatus.AVAILABLE ? "This vehicle is not currently bookable."
                : "Already booked for part of this period.";
        return new QuoteResponse(price, free, message);
    }

    @Transactional(readOnly = true)
    public List<BookedRange> bookedRanges(Long id) {
        vehicles.findById(id).orElseThrow(() -> ApiException.vehicleNotFound(id));
        return bookings.findByVehicleIdAndStatusInAndReturnDateGreaterThanOrderByPickupDateAsc(
                        id, BookingStatus.BLOCKING, LocalDate.now(clock)).stream()
                .map(b -> new BookedRange(b.getPickupDate(), b.getReturnDate())).toList();
    }

    @Transactional(readOnly = true)
    public MetaDto meta() {
        return new MetaDto(Arrays.asList(VehicleType.values()), Arrays.asList(FuelType.values()),
                Arrays.asList(Transmission.values()),
                locations.findAll().stream().map(Mappers::toDto).toList(), props.getCurrency());
    }

    // ------------------------------------------------------------------ admin CRUD

    @Transactional
    public VehicleDto create(VehicleRequest r, CurrentUser actor) {
        if (vehicles.existsByRegistrationNumberIgnoreCase(r.registrationNumber().trim())) {
            throw ApiException.conflict("DUPLICATE_REGISTRATION", "A vehicle with this registration number already exists.");
        }
        Vehicle v = new Vehicle();
        apply(v, r);
        v.setCreatedAt(clock.instant());
        Vehicle saved = vehicles.save(v);
        audit.log(actor.email(), "VEHICLE_CREATED", "Vehicle", saved.getId(),
                saved.getMake() + " " + saved.getModel() + " " + saved.getRegistrationNumber());
        return Mappers.toDto(saved);
    }

    @Transactional
    public VehicleDto update(Long id, VehicleRequest r, CurrentUser actor) {
        Vehicle v = vehicles.lockById(id).orElseThrow(() -> ApiException.vehicleNotFound(id));
        if (!v.getRegistrationNumber().equalsIgnoreCase(r.registrationNumber().trim())
                && vehicles.existsByRegistrationNumberIgnoreCase(r.registrationNumber().trim())) {
            throw ApiException.conflict("DUPLICATE_REGISTRATION", "A vehicle with this registration number already exists.");
        }
        if (r.status() == VehicleStatus.MAINTENANCE || r.status() == VehicleStatus.RETIRED) {
            guardNotRentedOut(v);
        }
        apply(v, r);
        audit.log(actor.email(), "VEHICLE_UPDATED", "Vehicle", id, v.getMake() + " " + v.getModel() + " status=" + v.getStatus());
        return Mappers.toDto(v);
    }

    /** Soft delete: the vehicle keeps its booking history but can no longer be searched or booked. */
    @Transactional
    public void retire(Long id, CurrentUser actor) {
        Vehicle v = vehicles.lockById(id).orElseThrow(() -> ApiException.vehicleNotFound(id));
        if (bookings.countOverlapping(id, LocalDate.now(clock), FAR_FUTURE, BookingStatus.BLOCKING) > 0) {
            throw ApiException.conflict("VEHICLE_HAS_BOOKINGS",
                    "This vehicle has active or upcoming bookings. Cancel them first or put it into maintenance.");
        }
        v.setStatus(VehicleStatus.RETIRED);
        audit.log(actor.email(), "VEHICLE_RETIRED", "Vehicle", id, v.getRegistrationNumber());
    }

    // ------------------------------------------------------------------ operational status & maintenance

    @Transactional
    public VehicleDto setOperationalStatus(Long id, VehicleStatus status, CurrentUser actor) {
        if (status == VehicleStatus.RETIRED) {
            throw ApiException.badRequest("INVALID_STATUS", "Retiring a vehicle is an administrator action (DELETE /api/admin/vehicles/{id}).");
        }
        Vehicle v = vehicles.lockById(id).orElseThrow(() -> ApiException.vehicleNotFound(id));
        if (v.getStatus() == VehicleStatus.RETIRED) {
            throw ApiException.conflict("VEHICLE_RETIRED", "A retired vehicle cannot change status.");
        }
        if (status == VehicleStatus.MAINTENANCE) {
            guardNotRentedOut(v);
        }
        VehicleStatus before = v.getStatus();
        v.setStatus(status);
        audit.log(actor.email(), "VEHICLE_STATUS_CHANGED", "Vehicle", id, before + " -> " + status);
        return Mappers.toDto(v);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceDto> maintenanceHistory(Long vehicleId) {
        vehicles.findById(vehicleId).orElseThrow(() -> ApiException.vehicleNotFound(vehicleId));
        return maintenance.findByVehicleIdOrderByPerformedOnDescIdDesc(vehicleId).stream().map(Mappers::toDto).toList();
    }

    @Transactional
    public MaintenanceDto addMaintenance(Long vehicleId, MaintenanceRequest r, CurrentUser actor) {
        Vehicle v = vehicles.lockById(vehicleId).orElseThrow(() -> ApiException.vehicleNotFound(vehicleId));
        MaintenanceRecord m = new MaintenanceRecord();
        m.setVehicle(v);
        m.setDescription(r.description());
        m.setPerformedOn(r.performedOn());
        m.setCost(r.cost());
        m.setOdometerKm(r.odometerKm());
        m.setNextServiceDate(r.nextServiceDate());
        m.setCreatedBy(actor.email());
        m.setCreatedAt(clock.instant());
        if (r.odometerKm() != null && r.odometerKm() > v.getMileageKm()) {
            v.setMileageKm(r.odometerKm());
        }
        if (r.markUnderMaintenance() && v.getStatus() == VehicleStatus.AVAILABLE) {
            guardNotRentedOut(v);
            v.setStatus(VehicleStatus.MAINTENANCE);
        }
        MaintenanceRecord saved = maintenance.save(m);
        audit.log(actor.email(), "MAINTENANCE_RECORDED", "Vehicle", vehicleId, r.description());
        return Mappers.toDto(saved);
    }

    // ------------------------------------------------------------------ helpers

    private void guardNotRentedOut(Vehicle v) {
        LocalDate today = LocalDate.now(clock);
        if (bookings.countOverlapping(v.getId(), today, today.plusDays(1), Set.of(BookingStatus.PICKED_UP)) > 0) {
            throw ApiException.conflict("VEHICLE_RENTED_OUT",
                    "The vehicle is currently rented out; record its return before changing the status.");
        }
    }

    private void apply(Vehicle v, VehicleRequest r) {
        Location loc = locations.findById(r.locationId()).orElseThrow(() -> ApiException.notFound("Location", r.locationId()));
        v.setRegistrationNumber(r.registrationNumber().trim().toUpperCase());
        v.setMake(r.make().trim());
        v.setModel(r.model().trim());
        v.setModelYear(r.modelYear());
        v.setVehicleType(r.vehicleType());
        v.setFuelType(r.fuelType());
        v.setTransmission(r.transmission());
        v.setSeats(r.seats());
        v.setDailyRate(r.dailyRate());
        v.setLocation(loc);
        v.setImageUrl(r.imageUrl() == null || r.imageUrl().isBlank() ? "/images/cars/" + r.vehicleType().name().toLowerCase() + ".svg" : r.imageUrl());
        v.setDescription(r.description());
        v.setMileageKm(r.mileageKm());
        if (r.status() != null) {
            v.setStatus(r.status());
        }
    }

    private static void validateFilters(BigDecimal min, BigDecimal max, LocalDate pickup, LocalDate ret,
                                        Integer minSeats, int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "page must be 0 or greater.");
        }
        if (size < 1 || size > 50) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "size must be between 1 and 50.");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw ApiException.badRequest("INVALID_PRICE_RANGE", "minPrice must not exceed maxPrice.");
        }
        if ((pickup == null) != (ret == null)) {
            throw ApiException.invalidDateRange("Provide both pickupDate and returnDate, or neither.");
        }
        if (minSeats != null && minSeats < 1) {
            throw ApiException.badRequest("INVALID_SEATS", "minSeats must be at least 1.");
        }
    }
}
