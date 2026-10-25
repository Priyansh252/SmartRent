package com.smartrent.service;

import com.smartrent.domain.Booking;
import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.OutboxStatus;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.domain.VehicleType;
import com.smartrent.config.AppProperties;
import com.smartrent.dto.Dtos.AdminCharts;
import com.smartrent.dto.Dtos.AdminDashboard;
import com.smartrent.dto.Dtos.DailyRevenue;
import com.smartrent.dto.Dtos.TypeUtilization;
import com.smartrent.repo.BookingRepository;
import com.smartrent.repo.NotificationRepository;
import com.smartrent.repo.OutboxRepository;
import com.smartrent.repo.VehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Numbers for the admin dashboard and charts. Everything is derived from stored bookings and vehicles. */
@Service
public class StatsService {

    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final OutboxRepository outbox;
    private final NotificationRepository notifications;
    private final AppProperties props;
    private final Clock clock;

    public StatsService(BookingRepository bookings, VehicleRepository vehicles, OutboxRepository outbox,
                        NotificationRepository notifications, AppProperties props, Clock clock) {
        this.bookings = bookings;
        this.vehicles = vehicles;
        this.outbox = outbox;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboard dashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(29);
        LocalDate to = today.plusDays(1);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus s : BookingStatus.values()) {
            byStatus.put(s.name(), bookings.countByStatus(s));
        }
        AdminCharts charts = charts(30);
        long rented = charts.utilizationByType().stream().mapToLong(TypeUtilization::rentedVehicleDays).sum();
        long capacity = charts.utilizationByType().stream().mapToLong(TypeUtilization::availableVehicleDays).sum();
        double util = capacity == 0 ? 0 : Math.round(1000.0 * rented / capacity) / 10.0;
        return new AdminDashboard(vehicles.count(), vehicles.countBookable(),
                vehicles.countByStatus(VehicleStatus.MAINTENANCE), vehicles.countByStatus(VehicleStatus.RETIRED),
                byStatus, bookings.revenueBetween(from, to), bookings.countPickupsBetween(from, to), util,
                outbox.countByStatus(OutboxStatus.PENDING), outbox.countByStatus(OutboxStatus.FAILED),
                notifications.count(), props.getCurrency());
    }

    /** Daily revenue (by pickup date) and fleet utilization per vehicle type for the last {@code days} days. */
    @Transactional(readOnly = true)
    public AdminCharts charts(int days) {
        int window = Math.min(Math.max(days, 7), 90);
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(window - 1L);
        LocalDate to = today.plusDays(1);   // exclusive
        List<Booking> list = bookings.findActiveBetween(from, to);

        Map<LocalDate, BigDecimal> revenue = new TreeMap<>();
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
            revenue.put(d, BigDecimal.ZERO);
            counts.put(d, 0L);
        }
        Map<VehicleType, Long> rentedDays = new EnumMap<>(VehicleType.class);
        for (Booking b : list) {
            if (revenue.containsKey(b.getPickupDate())) {
                revenue.merge(b.getPickupDate(), b.getTotalPrice(), BigDecimal::add);
                counts.merge(b.getPickupDate(), 1L, Long::sum);
            }
            LocalDate s = b.getPickupDate().isBefore(from) ? from : b.getPickupDate();
            LocalDate e = b.getReturnDate().isAfter(to) ? to : b.getReturnDate();
            long overlap = Math.max(0, ChronoUnit.DAYS.between(s, e));
            rentedDays.merge(b.getVehicle().getVehicleType(), overlap, Long::sum);
        }
        List<DailyRevenue> daily = new ArrayList<>();
        revenue.forEach((d, r) -> daily.add(new DailyRevenue(d, r.setScale(2, RoundingMode.HALF_UP), counts.get(d))));

        List<TypeUtilization> util = new ArrayList<>();
        long span = ChronoUnit.DAYS.between(from, to);
        for (VehicleType t : VehicleType.values()) {
            long fleet = vehicles.countBookableByType(t);
            long capacity = fleet * span;
            long rented = Math.min(rentedDays.getOrDefault(t, 0L), capacity);
            double pct = capacity == 0 ? 0 : Math.round(1000.0 * rented / capacity) / 10.0;
            util.add(new TypeUtilization(t, fleet, rented, capacity, pct));
        }
        return new AdminCharts(daily, util, window);
    }
}
