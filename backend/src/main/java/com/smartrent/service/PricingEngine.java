package com.smartrent.service;

import com.smartrent.domain.PeakPeriod;
import com.smartrent.domain.PricingConfig;
import com.smartrent.dto.Dtos.PriceBreakdown;
import com.smartrent.dto.Dtos.PriceLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic, explainable, rule-based pricing. No I/O: every input is passed in, so the same inputs always give
 * the same price and the class is trivially unit-testable.
 *
 * <pre>
 * Rental duration  : days = number of whole calendar days in [pickup, return)  (partial days are not supported)
 * subtotal         : baseDailyRate x days
 * rawMultiplier    : 1 + weekend + peak + demand + availability - durationDiscount
 *   weekend        : weekendSurcharge x (weekend days / days)                (Saturday and Sunday count)
 *   peak           : average over the rental days of the highest peak-period surcharge covering that day
 *   demand         : clamp((bookingsPerVehicle - baseline) x demandWeight, -maxDemandDiscount, +maxDemandSurcharge)
 *   availability   : (threshold - freeRatio) x availabilityWeight   when the share of free vehicles of this type
 *                    during the rental drops below the threshold, else 0
 *   durationDiscount : 7+ days / 14+ days / 30+ days tier discounts (largest tier that applies)
 * appliedMultiplier: clamp(rawMultiplier, minMultiplier, maxMultiplier)
 * total            : round(subtotal x appliedMultiplier, 2)
 * </pre>
 * Each adjustment is shown as its own line; a final LIMIT/ROUNDING line makes the lines sum exactly to the total.
 */
@Component
public class PricingEngine {

    private static final int FACTOR_SCALE = 4;
    private static final BigDecimal ONE = BigDecimal.ONE;

    /** Measured inputs derived from stored data (or explicit demo values in tests). */
    public record Signals(long recentBookings, long bookableVehicles, long blockedOfType, long totalOfType) {
    }

    public PriceBreakdown calculate(Long vehicleId, BigDecimal dailyRate, LocalDate pickup, LocalDate returnDate,
                                    PricingConfig cfg, List<PeakPeriod> peaks, Signals signals, String currency) {
        if (pickup == null || returnDate == null || !returnDate.isAfter(pickup)) {
            throw new IllegalArgumentException("returnDate must be after pickupDate");
        }
        int days = (int) ChronoUnit.DAYS.between(pickup, returnDate);
        BigDecimal daysBd = BigDecimal.valueOf(days);
        BigDecimal subtotal = money(dailyRate.multiply(daysBd));

        // ---- weekend ----
        int weekendDays = 0;
        // ---- peak ----
        BigDecimal peakSum = BigDecimal.ZERO;
        List<String> peakNames = new ArrayList<>();
        for (LocalDate d = pickup; d.isBefore(returnDate); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                weekendDays++;
            }
            PeakPeriod best = null;
            for (PeakPeriod p : peaks) {
                if (p.covers(d) && (best == null || p.getSurcharge().compareTo(best.getSurcharge()) > 0)) {
                    best = p;
                }
            }
            if (best != null) {
                peakSum = peakSum.add(best.getSurcharge());
                if (!peakNames.contains(best.getName())) {
                    peakNames.add(best.getName());
                }
            }
        }
        BigDecimal weekendFactor = cfg.getWeekendSurcharge()
                .multiply(BigDecimal.valueOf(weekendDays)).divide(daysBd, FACTOR_SCALE, RoundingMode.HALF_UP);
        BigDecimal peakFactor = peakSum.divide(daysBd, FACTOR_SCALE, RoundingMode.HALF_UP);

        // ---- demand ----
        BigDecimal demandLevel = signals.bookableVehicles() > 0
                ? BigDecimal.valueOf(signals.recentBookings())
                        .divide(BigDecimal.valueOf(signals.bookableVehicles()), FACTOR_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal demandFactor = demandLevel.subtract(cfg.getDemandBaseline()).multiply(cfg.getDemandWeight())
                .setScale(FACTOR_SCALE, RoundingMode.HALF_UP)
                .max(cfg.getMaxDemandDiscount().negate())
                .min(cfg.getMaxDemandSurcharge());

        // ---- availability ----
        BigDecimal freeRatio = signals.totalOfType() > 0
                ? BigDecimal.valueOf(signals.totalOfType() - signals.blockedOfType())
                        .divide(BigDecimal.valueOf(signals.totalOfType()), FACTOR_SCALE, RoundingMode.HALF_UP)
                : ONE;
        BigDecimal availabilityFactor = freeRatio.compareTo(cfg.getAvailabilityThreshold()) < 0
                ? cfg.getAvailabilityThreshold().subtract(freeRatio).multiply(cfg.getAvailabilityWeight())
                        .setScale(FACTOR_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(FACTOR_SCALE);

        // ---- duration discount ----
        BigDecimal durationDiscount = days >= 30 ? cfg.getDiscount30Days()
                : days >= 14 ? cfg.getDiscount14Days()
                : days >= 7 ? cfg.getDiscount7Days() : BigDecimal.ZERO;
        BigDecimal durationFactor = durationDiscount.negate().setScale(FACTOR_SCALE, RoundingMode.HALF_UP);

        BigDecimal raw = ONE.add(weekendFactor).add(peakFactor).add(demandFactor).add(availabilityFactor)
                .add(durationFactor).setScale(FACTOR_SCALE, RoundingMode.HALF_UP);
        BigDecimal applied = raw.max(cfg.getMinMultiplier()).min(cfg.getMaxMultiplier())
                .setScale(FACTOR_SCALE, RoundingMode.HALF_UP);
        boolean clamped = applied.compareTo(raw) != 0;
        BigDecimal total = money(subtotal.multiply(applied));

        // ---- explanation lines ----
        List<PriceLine> lines = new ArrayList<>();
        lines.add(new PriceLine("BASE", "Base rate (" + days + (days == 1 ? " day" : " days") + ")", subtotal,
                currency + " " + money(dailyRate) + " x " + days));
        BigDecimal running = subtotal;
        running = addLine(lines, running, "WEEKEND", "Weekend surcharge", subtotal, weekendFactor,
                weekendDays + " of " + days + " days fall on a weekend");
        running = addLine(lines, running, "PEAK", "Peak-season surcharge", subtotal, peakFactor,
                peakNames.isEmpty() ? "no peak period" : "peak period(s): " + String.join(", ", peakNames));
        running = addLine(lines, running, "DEMAND", demandFactor.signum() >= 0 ? "High-demand surcharge" : "Low-demand discount",
                subtotal, demandFactor, demandLevel.toPlainString() + " recent bookings per vehicle vs baseline "
                        + cfg.getDemandBaseline().toPlainString());
        running = addLine(lines, running, "AVAILABILITY", "Limited-availability surcharge", subtotal, availabilityFactor,
                pct(freeRatio) + " of this vehicle type is free for these dates (threshold "
                        + pct(cfg.getAvailabilityThreshold()) + ")");
        running = addLine(lines, running, "DURATION", "Long-rental discount", subtotal, durationFactor,
                days + " days qualifies for a " + pct(durationDiscount) + " discount");
        BigDecimal remainder = total.subtract(running);
        if (remainder.signum() != 0) {
            lines.add(new PriceLine(clamped ? "LIMIT" : "ROUNDING",
                    clamped ? "Price-limit adjustment" : "Rounding", remainder,
                    clamped ? "multiplier " + raw.toPlainString() + " limited to " + applied.toPlainString()
                            + " (allowed " + cfg.getMinMultiplier().toPlainString() + "-" + cfg.getMaxMultiplier().toPlainString() + ")"
                            : "rounded to 2 decimals"));
        }

        Map<String, String> sig = new LinkedHashMap<>();
        sig.put("weekendDays", String.valueOf(weekendDays));
        sig.put("peakPeriods", peakNames.isEmpty() ? "none" : String.join(", ", peakNames));
        sig.put("recentBookings", String.valueOf(signals.recentBookings()));
        sig.put("bookableVehicles", String.valueOf(signals.bookableVehicles()));
        sig.put("demandLevel", demandLevel.toPlainString());
        sig.put("freeVehicleRatio", freeRatio.toPlainString());
        sig.put("blockedVehiclesOfType", String.valueOf(signals.blockedOfType()));
        sig.put("totalVehiclesOfType", String.valueOf(signals.totalOfType()));

        BigDecimal avg = total.divide(daysBd, 2, RoundingMode.HALF_UP);
        return new PriceBreakdown(vehicleId, pickup, returnDate, days, currency, money(dailyRate), List.copyOf(lines),
                subtotal, raw, applied, clamped, total, avg, sig);
    }

    private BigDecimal addLine(List<PriceLine> lines, BigDecimal running, String code, String label,
                               BigDecimal subtotal, BigDecimal factor, String why) {
        if (factor.signum() == 0) {
            return running;
        }
        BigDecimal amount = money(subtotal.multiply(factor));
        lines.add(new PriceLine(code, label + " (" + signedPct(factor) + ")", amount, why));
        return running.add(amount);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String pct(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString() + "%";
    }

    private static String signedPct(BigDecimal ratio) {
        return (ratio.signum() >= 0 ? "+" : "") + pct(ratio);
    }

    /** Helper for tests/diagnostics. */
    public static String describe(PriceBreakdown b) {
        return b.lines().stream().map(l -> l.code() + "=" + l.amount()).collect(Collectors.joining(", "))
                + " -> " + b.totalPrice();
    }
}
