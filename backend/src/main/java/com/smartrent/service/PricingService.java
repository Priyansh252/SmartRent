package com.smartrent.service;

import com.smartrent.config.AppProperties;
import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.PeakPeriod;
import com.smartrent.domain.PricingConfig;
import com.smartrent.domain.Vehicle;
import com.smartrent.dto.Dtos.PeakPeriodDto;
import com.smartrent.dto.Dtos.PriceBreakdown;
import com.smartrent.dto.Dtos.PricingConfigDto;
import com.smartrent.repo.BookingRepository;
import com.smartrent.repo.PeakPeriodRepository;
import com.smartrent.repo.PricingConfigRepository;
import com.smartrent.repo.VehicleRepository;
import com.smartrent.web.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gathers the measured signals (recent bookings, fleet size, blocked vehicles) from the database and delegates the
 * arithmetic to the pure {@link PricingEngine}. Also owns the admin-editable pricing configuration and peak periods.
 */
@Service
public class PricingService {

    private final PricingConfigRepository configRepo;
    private final PeakPeriodRepository peakRepo;
    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final PricingEngine engine;
    private final AppProperties props;
    private final Clock clock;
    private final AuditService audit;

    public PricingService(PricingConfigRepository configRepo, PeakPeriodRepository peakRepo, BookingRepository bookings,
                          VehicleRepository vehicles, PricingEngine engine, AppProperties props, Clock clock,
                          AuditService audit) {
        this.configRepo = configRepo;
        this.peakRepo = peakRepo;
        this.bookings = bookings;
        this.vehicles = vehicles;
        this.engine = engine;
        this.props = props;
        this.clock = clock;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ quoting

    @Transactional(readOnly = true)
    public PriceBreakdown quote(Vehicle vehicle, LocalDate pickup, LocalDate returnDate) {
        PricingConfig cfg = currentConfig();
        List<PeakPeriod> peaks = peakRepo.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                returnDate.minusDays(1), pickup);
        Instant since = clock.instant().minus(cfg.getDemandWindowDays(), ChronoUnit.DAYS);
        PricingEngine.Signals signals = new PricingEngine.Signals(
                bookings.countCreatedSince(since),
                vehicles.countBookable(),
                bookings.countBlockedVehiclesOfType(vehicle.getVehicleType(), pickup, returnDate, BookingStatus.BLOCKING),
                vehicles.countBookableByType(vehicle.getVehicleType()));
        return engine.calculate(vehicle.getId(), vehicle.getDailyRate(), pickup, returnDate, cfg, peaks, signals,
                props.getCurrency());
    }

    // ------------------------------------------------------------------ configuration

    @Transactional(readOnly = true)
    public PricingConfig currentConfig() {
        return configRepo.findById(PricingConfig.SINGLETON_ID).orElseGet(PricingConfig::defaults);
    }

    @Transactional(readOnly = true)
    public PricingConfigDto getConfigDto() {
        return toDto(currentConfig());
    }

    @Transactional
    public PricingConfigDto updateConfig(PricingConfigDto d, String actor) {
        if (d.minMultiplier().compareTo(d.maxMultiplier()) > 0) {
            throw ApiException.badRequest("INVALID_PRICING_CONFIG", "minMultiplier must not exceed maxMultiplier.");
        }
        if (d.discount7Days().compareTo(d.discount14Days()) > 0 || d.discount14Days().compareTo(d.discount30Days()) > 0) {
            throw ApiException.badRequest("INVALID_PRICING_CONFIG",
                    "Long-rental discounts must not decrease: 7 days <= 14 days <= 30 days.");
        }
        PricingConfig c = currentConfig();
        c.setWeekendSurcharge(d.weekendSurcharge());
        c.setDemandWindowDays(d.demandWindowDays());
        c.setDemandBaseline(d.demandBaseline());
        c.setDemandWeight(d.demandWeight());
        c.setMaxDemandSurcharge(d.maxDemandSurcharge());
        c.setMaxDemandDiscount(d.maxDemandDiscount());
        c.setAvailabilityWeight(d.availabilityWeight());
        c.setAvailabilityThreshold(d.availabilityThreshold());
        c.setDiscount7Days(d.discount7Days());
        c.setDiscount14Days(d.discount14Days());
        c.setDiscount30Days(d.discount30Days());
        c.setMinMultiplier(d.minMultiplier());
        c.setMaxMultiplier(d.maxMultiplier());
        c.setFreeCancelDays(d.freeCancelDays());
        c.setLateCancelFeeRate(d.lateCancelFeeRate());
        c.setUpdatedAt(clock.instant());
        c.setUpdatedBy(actor);
        PricingConfig saved = configRepo.save(c);
        audit.log(actor, "PRICING_CONFIG_UPDATED", "PricingConfig", saved.getId(),
                "multiplier range " + d.minMultiplier() + "-" + d.maxMultiplier() + ", weekend " + d.weekendSurcharge());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PeakPeriodDto> listPeaks() {
        return peakRepo.findAllByOrderByStartDateAsc().stream().map(PricingService::toDto).toList();
    }

    @Transactional
    public PeakPeriodDto savePeak(Long id, PeakPeriodDto d, String actor) {
        if (d.endDate().isBefore(d.startDate())) {
            throw ApiException.invalidDateRange("endDate must not be before startDate.");
        }
        PeakPeriod p = id == null ? new PeakPeriod()
                : peakRepo.findById(id).orElseThrow(() -> ApiException.notFound("Peak period", id));
        p.setName(d.name());
        p.setStartDate(d.startDate());
        p.setEndDate(d.endDate());
        p.setSurcharge(d.surcharge());
        p.setActive(d.active());
        PeakPeriod saved = peakRepo.save(p);
        audit.log(actor, id == null ? "PEAK_PERIOD_CREATED" : "PEAK_PERIOD_UPDATED", "PeakPeriod", saved.getId(),
                saved.getName() + " " + saved.getStartDate() + ".." + saved.getEndDate() + " +" + saved.getSurcharge());
        return toDto(saved);
    }

    @Transactional
    public void deletePeak(Long id, String actor) {
        PeakPeriod p = peakRepo.findById(id).orElseThrow(() -> ApiException.notFound("Peak period", id));
        peakRepo.delete(p);
        audit.log(actor, "PEAK_PERIOD_DELETED", "PeakPeriod", id, p.getName());
    }

    static PricingConfigDto toDto(PricingConfig c) {
        return new PricingConfigDto(c.getWeekendSurcharge(), c.getDemandWindowDays(), c.getDemandBaseline(),
                c.getDemandWeight(), c.getMaxDemandSurcharge(), c.getMaxDemandDiscount(), c.getAvailabilityWeight(),
                c.getAvailabilityThreshold(), c.getDiscount7Days(), c.getDiscount14Days(), c.getDiscount30Days(),
                c.getMinMultiplier(), c.getMaxMultiplier(), c.getFreeCancelDays(), c.getLateCancelFeeRate(),
                c.getUpdatedAt(), c.getUpdatedBy());
    }

    static PeakPeriodDto toDto(PeakPeriod p) {
        return new PeakPeriodDto(p.getId(), p.getName(), p.getStartDate(), p.getEndDate(), p.getSurcharge(), p.isActive());
    }
}
