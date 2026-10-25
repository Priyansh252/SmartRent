package com.smartrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartrent.domain.PeakPeriod;
import com.smartrent.domain.PricingConfig;
import com.smartrent.dto.Dtos.PriceBreakdown;
import com.smartrent.service.PricingEngine;
import com.smartrent.service.PricingEngine.Signals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring context, no database. */
class PricingEngineTest {

    private final PricingEngine engine = new PricingEngine();
    private final PricingConfig cfg = PricingConfig.defaults();
    // 2030-01-07 is a Monday
    private static final LocalDate MON = LocalDate.of(2030, 1, 7);
    private static final Signals NEUTRAL = new Signals(0, 10, 0, 10);

    private PriceBreakdown calc(LocalDate from, LocalDate to, List<PeakPeriod> peaks, Signals s) {
        return engine.calculate(1L, new BigDecimal("1000.00"), from, to, cfg, peaks, s, "INR");
    }

    @Test
    void weekdayRentalWithQuietDemandIsBaseRateTimesDaysMinusDemandDiscount() {
        PriceBreakdown p = calc(MON, MON.plusDays(2), List.of(), NEUTRAL);
        assertThat(p.days()).isEqualTo(2);
        assertThat(p.subtotal()).isEqualByComparingTo("2000.00");
        assertThat(p.totalPrice()).isLessThanOrEqualTo(new BigDecimal("2000.00"));
    }

    @Test
    void weekendDaysCostMoreThanWeekdays() {
        PriceBreakdown weekday = calc(MON, MON.plusDays(2), List.of(), NEUTRAL);
        PriceBreakdown weekend = calc(MON.plusDays(5), MON.plusDays(7), List.of(), NEUTRAL); // Sat + Sun
        assertThat(weekend.totalPrice()).isGreaterThan(weekday.totalPrice());
    }

    @Test
    void peakPeriodAddsSurcharge() {
        PeakPeriod peak = new PeakPeriod();
        peak.setName("Festival");
        peak.setStartDate(MON);
        peak.setEndDate(MON.plusDays(10));
        peak.setSurcharge(new BigDecimal("0.30"));
        peak.setActive(true);
        PriceBreakdown with = calc(MON, MON.plusDays(2), List.of(peak), NEUTRAL);
        PriceBreakdown without = calc(MON, MON.plusDays(2), List.of(), NEUTRAL);
        assertThat(with.totalPrice()).isGreaterThan(without.totalPrice());
    }

    @Test
    void longerRentalsGetADurationDiscount() {
        PriceBreakdown shortRental = calc(MON, MON.plusDays(3), List.of(), NEUTRAL);
        PriceBreakdown longRental = calc(MON, MON.plusDays(14), List.of(), NEUTRAL);
        assertThat(longRental.averageDailyPrice()).isLessThan(shortRental.averageDailyPrice());
    }

    @Test
    void lowAvailabilityRaisesThePrice() {
        PriceBreakdown plenty = calc(MON, MON.plusDays(2), List.of(), new Signals(0, 10, 0, 10));
        PriceBreakdown scarce = calc(MON, MON.plusDays(2), List.of(), new Signals(0, 10, 9, 10));
        assertThat(scarce.totalPrice()).isGreaterThan(plenty.totalPrice());
    }

    @Test
    void multiplierIsClampedToConfiguredLimits() {
        cfg.setMaxMultiplier(new BigDecimal("1.0500"));
        PeakPeriod peak = new PeakPeriod();
        peak.setName("Huge");
        peak.setStartDate(MON);
        peak.setEndDate(MON.plusDays(10));
        peak.setSurcharge(new BigDecimal("0.90"));
        peak.setActive(true);
        PriceBreakdown p = calc(MON, MON.plusDays(2), List.of(peak), new Signals(100, 10, 10, 10));
        assertThat(p.multiplierClamped()).isTrue();
        assertThat(p.appliedMultiplier()).isEqualByComparingTo("1.0500");
        assertThat(p.totalPrice()).isEqualByComparingTo("2100.00");
    }

    @Test
    void breakdownLinesSumToTheTotal() {
        PriceBreakdown p = calc(MON.plusDays(4), MON.plusDays(9), List.of(), new Signals(8, 10, 6, 10));
        BigDecimal sum = p.lines().stream().map(l -> l.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(p.totalPrice());
    }

    @Test
    void sameInputsAlwaysGiveTheSamePrice() {
        assertThat(calc(MON, MON.plusDays(3), List.of(), NEUTRAL).totalPrice())
                .isEqualByComparingTo(calc(MON, MON.plusDays(3), List.of(), NEUTRAL).totalPrice());
    }

    @Test
    void invalidRangeIsRejected() {
        assertThatThrownBy(() -> calc(MON, MON, List.of(), NEUTRAL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc(MON, MON.minusDays(1), List.of(), NEUTRAL)).isInstanceOf(IllegalArgumentException.class);
    }
}
