package com.smartrent.seed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrent.config.AppProperties;
import com.smartrent.domain.AppUser;
import com.smartrent.domain.Booking;
import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.BookingStatusHistory;
import com.smartrent.domain.EventType;
import com.smartrent.domain.FuelType;
import com.smartrent.domain.Location;
import com.smartrent.domain.MaintenanceRecord;
import com.smartrent.domain.Notification;
import com.smartrent.domain.NotificationStatus;
import com.smartrent.domain.PeakPeriod;
import com.smartrent.domain.Role;
import com.smartrent.domain.Transmission;
import com.smartrent.domain.Vehicle;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.domain.VehicleType;
import com.smartrent.dto.Dtos.PriceBreakdown;
import com.smartrent.repo.BookingRepository;
import com.smartrent.repo.BookingStatusHistoryRepository;
import com.smartrent.repo.LocationRepository;
import com.smartrent.repo.MaintenanceRecordRepository;
import com.smartrent.repo.NotificationRepository;
import com.smartrent.repo.PeakPeriodRepository;
import com.smartrent.repo.UserRepository;
import com.smartrent.repo.VehicleRepository;
import com.smartrent.service.PricingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reproducible demo data, created once on an empty database (idempotent: skipped when users already exist).
 * Dates are relative to "today" so the demo always has past, current and upcoming rentals.
 * Seeded bookings are written directly (they do not publish JMS events); only a few sample notifications are inserted.
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "smartrent.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final LocationRepository locations;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final PeakPeriodRepository peaks;
    private final MaintenanceRecordRepository maintenance;
    private final NotificationRepository notifications;
    private final PricingService pricing;
    private final PasswordEncoder encoder;
    private final ObjectMapper mapper;
    private final AppProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public DataSeeder(UserRepository users, LocationRepository locations, VehicleRepository vehicles,
                      BookingRepository bookings, BookingStatusHistoryRepository history, PeakPeriodRepository peaks,
                      MaintenanceRecordRepository maintenance, NotificationRepository notifications,
                      PricingService pricing, PasswordEncoder encoder, ObjectMapper mapper, AppProperties props,
                      Clock clock, PlatformTransactionManager txManager) {
        this.users = users;
        this.locations = locations;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.history = history;
        this.peaks = peaks;
        this.maintenance = maintenance;
        this.notifications = notifications;
        this.pricing = pricing;
        this.encoder = encoder;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("Seed skipped: database already contains users.");
            return;
        }
        tx.executeWithoutResult(status -> seed());
        log.info("Seed data created: {} users, {} vehicles, {} bookings.", users.count(), vehicles.count(), bookings.count());
    }

    private void seed() {
        // ---------------- accounts
        AppProperties.Seed s = props.getSeed();
        AppUser admin = user("Asha Admin", "admin@smartrent.demo", "+91 90000 00001", s.getAdminPassword(), Role.ADMIN);
        user("Ethan Employee", "employee@smartrent.demo", "+91 90000 00002", s.getEmployeePassword(), Role.EMPLOYEE);
        user("Eva Counter", "eva.employee@smartrent.demo", "+91 90000 00003", s.getEmployeePassword(), Role.EMPLOYEE);
        List<AppUser> customers = new ArrayList<>();
        String[][] people = {
                {"Chitra Customer", "customer@smartrent.demo"}, {"Arjun Mehta", "arjun.mehta@smartrent.demo"},
                {"Priya Nair", "priya.nair@smartrent.demo"}, {"Rohan Iyer", "rohan.iyer@smartrent.demo"},
                {"Sneha Kulkarni", "sneha.kulkarni@smartrent.demo"}, {"Vikram Rao", "vikram.rao@smartrent.demo"}};
        int n = 4;
        for (String[] p : people) {
            customers.add(user(p[0], p[1], "+91 98000 0000" + (n++), s.getCustomerPassword(), Role.CUSTOMER));
        }

        // ---------------- locations
        Location blr = locations.save(new Location("Bengaluru Airport (BLR)", "Bengaluru", "Kempegowda International Airport, Devanahalli", 13.1986, 77.7066));
        Location bom = locations.save(new Location("Mumbai Andheri East", "Mumbai", "Andheri-Kurla Road, Andheri East", 19.1136, 72.8697));
        Location del = locations.save(new Location("Delhi Connaught Place", "Delhi", "Block F, Connaught Place", 28.6315, 77.2167));
        Location maa = locations.save(new Location("Chennai Central", "Chennai", "Poonamallee High Road, near Central Station", 13.0827, 80.2707));
        Location[] locs = {blr, bom, del, maa};

        // ---------------- fleet (daily base rates in INR)
        Object[][] fleet = {
                // make, model, year, type, fuel, trans, seats, rate, locIdx, km
                {"Maruti Suzuki", "Swift", 2023, VehicleType.HATCHBACK, FuelType.PETROL, Transmission.MANUAL, 5, 1500, 0, 18200},
                {"Hyundai", "i20", 2023, VehicleType.HATCHBACK, FuelType.PETROL, Transmission.AUTOMATIC, 5, 1900, 1, 14500},
                {"Tata", "Altroz", 2022, VehicleType.HATCHBACK, FuelType.DIESEL, Transmission.MANUAL, 5, 1700, 2, 31000},
                {"Volkswagen", "Polo", 2022, VehicleType.HATCHBACK, FuelType.PETROL, Transmission.AUTOMATIC, 5, 2100, 3, 27800},
                {"Honda", "City", 2023, VehicleType.SEDAN, FuelType.PETROL, Transmission.AUTOMATIC, 5, 2900, 0, 12100},
                {"Hyundai", "Verna", 2023, VehicleType.SEDAN, FuelType.PETROL, Transmission.AUTOMATIC, 5, 2800, 1, 16400},
                {"Skoda", "Slavia", 2022, VehicleType.SEDAN, FuelType.PETROL, Transmission.MANUAL, 5, 2600, 2, 29800},
                {"Toyota", "Camry Hybrid", 2023, VehicleType.SEDAN, FuelType.HYBRID, Transmission.AUTOMATIC, 5, 4200, 3, 9800},
                {"Hyundai", "Creta", 2023, VehicleType.SUV, FuelType.DIESEL, Transmission.AUTOMATIC, 5, 3800, 0, 15200},
                {"Kia", "Seltos", 2023, VehicleType.SUV, FuelType.PETROL, Transmission.AUTOMATIC, 5, 3700, 1, 13300},
                {"Mahindra", "XUV700", 2023, VehicleType.SUV, FuelType.DIESEL, Transmission.AUTOMATIC, 7, 5200, 2, 11900},
                {"Tata", "Harrier", 2022, VehicleType.SUV, FuelType.DIESEL, Transmission.MANUAL, 5, 4300, 3, 34000},
                {"Toyota", "Fortuner", 2022, VehicleType.SUV, FuelType.DIESEL, Transmission.AUTOMATIC, 7, 6800, 1, 28400},
                {"Maruti Suzuki", "Ertiga", 2023, VehicleType.MPV, FuelType.CNG, Transmission.MANUAL, 7, 3100, 0, 21000},
                {"Kia", "Carens", 2023, VehicleType.MPV, FuelType.DIESEL, Transmission.AUTOMATIC, 7, 3600, 2, 14100},
                {"Toyota", "Innova Crysta", 2022, VehicleType.MPV, FuelType.DIESEL, Transmission.MANUAL, 7, 4400, 3, 38500},
                {"Kia", "Carnival", 2023, VehicleType.MPV, FuelType.DIESEL, Transmission.AUTOMATIC, 8, 6200, 1, 10300},
                {"Mercedes-Benz", "E-Class", 2023, VehicleType.LUXURY, FuelType.PETROL, Transmission.AUTOMATIC, 5, 11500, 1, 8900},
                {"BMW", "5 Series", 2023, VehicleType.LUXURY, FuelType.DIESEL, Transmission.AUTOMATIC, 5, 10800, 2, 9400},
                {"Audi", "A6", 2022, VehicleType.LUXURY, FuelType.PETROL, Transmission.AUTOMATIC, 5, 10200, 0, 15600},
                {"Tata", "Nexon EV", 2023, VehicleType.ELECTRIC, FuelType.ELECTRIC, Transmission.AUTOMATIC, 5, 3900, 0, 11200},
                {"MG", "ZS EV", 2023, VehicleType.ELECTRIC, FuelType.ELECTRIC, Transmission.AUTOMATIC, 5, 4300, 3, 12600},
                {"Hyundai", "Ioniq 5", 2023, VehicleType.ELECTRIC, FuelType.ELECTRIC, Transmission.AUTOMATIC, 5, 6900, 2, 7200},
                {"BYD", "Atto 3", 2023, VehicleType.ELECTRIC, FuelType.ELECTRIC, Transmission.AUTOMATIC, 5, 5400, 1, 9100}};
        String[] states = {"KA01", "MH02", "DL01", "TN09"};
        List<Vehicle> saved = new ArrayList<>();
        int serial = 1001;
        for (Object[] f : fleet) {
            Vehicle v = new Vehicle();
            int li = (int) f[8];
            v.setRegistrationNumber(states[li] + "SR" + serial++);
            v.setMake((String) f[0]);
            v.setModel((String) f[1]);
            v.setModelYear((int) f[2]);
            v.setVehicleType((VehicleType) f[3]);
            v.setFuelType((FuelType) f[4]);
            v.setTransmission((Transmission) f[5]);
            v.setSeats((int) f[6]);
            v.setDailyRate(new BigDecimal((int) f[7]).setScale(2));
            v.setLocation(locs[li]);
            v.setMileageKm((int) f[9]);
            v.setImageUrl("/images/cars/" + ((VehicleType) f[3]).name().toLowerCase() + ".svg");
            v.setDescription(f[0] + " " + f[1] + " - " + ((VehicleType) f[3]).name().toLowerCase() + ", "
                    + ((FuelType) f[4]).name().toLowerCase() + ", " + f[6] + " seats.");
            v.setStatus(VehicleStatus.AVAILABLE);
            saved.add(vehicles.save(v));
        }
        // two vehicles in maintenance, with service history
        Vehicle inService = saved.get(15);
        inService.setStatus(VehicleStatus.MAINTENANCE);
        Vehicle inService2 = saved.get(22);
        inService2.setStatus(VehicleStatus.MAINTENANCE);
        LocalDate today = LocalDate.now(clock);
        for (Vehicle v : List.of(inService, inService2, saved.get(0), saved.get(8))) {
            MaintenanceRecord m = new MaintenanceRecord();
            m.setVehicle(v);
            m.setDescription(v.getStatus() == VehicleStatus.MAINTENANCE ? "Scheduled 40,000 km service and brake inspection" : "Routine service and tyre rotation");
            m.setPerformedOn(today.minusDays(v.getStatus() == VehicleStatus.MAINTENANCE ? 1 : 35));
            m.setCost(new BigDecimal(v.getStatus() == VehicleStatus.MAINTENANCE ? "12400.00" : "6800.00"));
            m.setOdometerKm(v.getMileageKm());
            m.setNextServiceDate(today.plusDays(120));
            m.setCreatedBy(admin.getEmail());
            maintenance.save(m);
        }

        // ---------------- peak periods
        peaks.save(new PeakPeriod("Upcoming long weekend", today.plusDays(9), today.plusDays(12), new BigDecimal("0.2000")));
        int year = today.getYear();
        peaks.save(new PeakPeriod("Festive season", LocalDate.of(year, 10, 20), LocalDate.of(year, 11, 15), new BigDecimal("0.2500")));
        peaks.save(new PeakPeriod("Year-end holidays", LocalDate.of(year, 12, 20), LocalDate.of(year + 1, 1, 3), new BigDecimal("0.3000")));
        peaks.save(new PeakPeriod("Summer vacation", LocalDate.of(year + 1, 4, 15), LocalDate.of(year + 1, 6, 15), new BigDecimal("0.1500")));

        // ---------------- bookings in every lifecycle state (never overlapping per vehicle)
        Random rnd = new Random(42);
        Instant now = clock.instant();
        List<Booking> recent = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            Vehicle v = saved.get(i);
            if (v.getStatus() != VehicleStatus.AVAILABLE) {
                continue;
            }
            LocalDate cursor = today.minusDays(50 + rnd.nextInt(10));
            // one vehicle in three stays free for the next weeks so searches always have results
            LocalDate horizon = today.plusDays(i % 3 == 0 ? 3 : 24);
            while (cursor.isBefore(horizon)) {
                cursor = cursor.plusDays(1 + rnd.nextInt(6));
                int len = 1 + rnd.nextInt(6);
                LocalDate pickup = cursor;
                LocalDate ret = pickup.plusDays(len);
                cursor = ret;
                if (!pickup.isBefore(horizon)) {
                    break;
                }
                AppUser customer = customers.get(rnd.nextInt(customers.size()));
                Booking b = newBooking(v, customer, pickup, ret, rnd, today, now);
                if (b.getCreatedAt().isAfter(now.minus(7, ChronoUnit.DAYS)) && b.getStatus() != BookingStatus.CANCELLED) {
                    recent.add(b);
                }
            }
        }
        // a few sample notifications for the most recent bookings
        int count = 0;
        for (Booking b : recent) {
            if (count++ >= 8) {
                break;
            }
            Notification nt = new Notification();
            nt.setEventId(UUID.randomUUID().toString());
            nt.setEventType(EventType.BOOKING_CONFIRMED);
            nt.setBookingId(b.getId());
            nt.setUserId(b.getUser().getId());
            nt.setTitle("Booking confirmed");
            nt.setMessage("Booking " + b.getReference() + " is confirmed: " + b.getVehicle().getMake() + " "
                    + b.getVehicle().getModel() + ", " + b.getPickupDate() + " to " + b.getReturnDate()
                    + ". Agreed total " + props.getCurrency() + " " + b.getTotalPrice() + ".");
            nt.setStatus(NotificationStatus.PROCESSED);
            nt.setCreatedAt(b.getCreatedAt());
            nt.setProcessedAt(b.getCreatedAt().plusSeconds(2));
            notifications.save(nt);
        }
    }

    private Booking newBooking(Vehicle v, AppUser customer, LocalDate pickup, LocalDate ret, Random rnd,
                               LocalDate today, Instant now) {
        PriceBreakdown quote = pricing.quote(v, pickup, ret);
        Booking b = new Booking();
        b.setReference("SR-" + Long.toString(Math.abs(rnd.nextLong()), 36).toUpperCase().replaceAll("[^A-Z0-9]", "X")
                .substring(0, 8));
        b.setUser(customer);
        b.setVehicle(v);
        b.setPickupDate(pickup);
        b.setReturnDate(ret);
        b.setDailyBaseRate(v.getDailyRate());
        b.setTotalPrice(quote.totalPrice());
        try {
            b.setPriceBreakdown(mapper.writeValueAsString(quote));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        boolean cancelled = rnd.nextInt(100) < 10;
        Instant created;
        BookingStatus status;
        if (!ret.isAfter(today)) {
            status = cancelled ? BookingStatus.CANCELLED : BookingStatus.RETURNED;
            created = at(pickup.minusDays(2 + rnd.nextInt(12)));
        } else if (!pickup.isAfter(today)) {
            status = BookingStatus.PICKED_UP;
            created = at(pickup.minusDays(1 + rnd.nextInt(7)));
        } else {
            status = cancelled ? BookingStatus.CANCELLED : BookingStatus.CONFIRMED;
            created = now.minus(rnd.nextInt(13 * 24), ChronoUnit.HOURS);   // future pickups were booked recently
            if (created.isAfter(at(pickup))) {
                created = now.minus(1, ChronoUnit.HOURS);
            }
        }
        if (created.isAfter(now)) {
            created = now.minus(1, ChronoUnit.HOURS);
        }
        b.setCreatedAt(created);
        b.setStatus(status);
        if (status == BookingStatus.CANCELLED) {
            b.setCancelledAt(created.plus(6, ChronoUnit.HOURS).isAfter(now) ? now : created.plus(6, ChronoUnit.HOURS));
            b.setCancellationFee(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        if (status == BookingStatus.PICKED_UP || status == BookingStatus.RETURNED) {
            b.setPickedUpAt(at(pickup).plus(10, ChronoUnit.HOURS));
        }
        if (status == BookingStatus.RETURNED) {
            b.setReturnedAt(at(ret).plus(9, ChronoUnit.HOURS));
        }
        b = bookings.save(b);

        String staff = "employee@smartrent.demo";
        history.save(new BookingStatusHistory(b.getId(), null, BookingStatus.CONFIRMED, customer.getEmail(), "Booking created"));
        if (status == BookingStatus.CANCELLED) {
            history.save(new BookingStatusHistory(b.getId(), BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
                    customer.getEmail(), "Cancelled (no fee)"));
        }
        if (status == BookingStatus.PICKED_UP || status == BookingStatus.RETURNED) {
            history.save(new BookingStatusHistory(b.getId(), BookingStatus.CONFIRMED, BookingStatus.PICKED_UP, staff, "Vehicle handed over"));
        }
        if (status == BookingStatus.RETURNED) {
            history.save(new BookingStatusHistory(b.getId(), BookingStatus.PICKED_UP, BookingStatus.RETURNED, staff, "Vehicle returned"));
        }
        return b;
    }

    private Instant at(LocalDate d) {
        return d.atStartOfDay(clock.getZone()).toInstant();
    }

    private AppUser user(String name, String email, String phone, String rawPassword, Role role) {
        return users.save(new AppUser(name, email, phone, encoder.encode(rawPassword), role));
    }
}
