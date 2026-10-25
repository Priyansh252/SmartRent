package com.smartrent.batch;

import com.smartrent.domain.Booking;
import com.smartrent.domain.ReportType;
import com.smartrent.repo.BookingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.item.ItemProcessor;

/**
 * Pass-through for the SQL-aggregated reports. For FLEET_UTILIZATION it enriches each vehicle row with the number of
 * rental days booked inside [from, to) (clipped to the window; cancelled bookings excluded) and the utilization %.
 */
public class ReportProcessor implements ItemProcessor<List<String>, List<String>> {

    private final ReportType type;
    private final LocalDate from;
    private final LocalDate to;
    private final BookingRepository bookings;

    public ReportProcessor(ReportType type, LocalDate from, LocalDate to, BookingRepository bookings) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.bookings = bookings;
    }

    @Override
    public List<String> process(List<String> row) {
        if (type != ReportType.FLEET_UTILIZATION) {
            return row;
        }
        long vehicleId = Long.parseLong(row.get(0));
        long periodDays = ChronoUnit.DAYS.between(from, to);
        long booked = 0;
        for (Booking b : bookings.findActiveOverlapping(vehicleId, from, to)) {
            LocalDate start = b.getPickupDate().isBefore(from) ? from : b.getPickupDate();
            LocalDate end = b.getReturnDate().isAfter(to) ? to : b.getReturnDate();
            booked += Math.max(0, ChronoUnit.DAYS.between(start, end));
        }
        BigDecimal pct = periodDays == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(booked * 100.0).divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
        List<String> out = new ArrayList<>();
        out.add(row.get(1));
        out.add(row.get(2));
        out.add(row.get(3));
        out.add(String.valueOf(booked));
        out.add(String.valueOf(periodDays));
        out.add(pct.toPlainString());
        return out;
    }
}
