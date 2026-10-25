package com.smartrent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single-row table (id = 1) holding every tunable weight of the pricing engine. */
@Entity
@Table(name = "pricing_config")
@Getter
@Setter
@NoArgsConstructor
public class PricingConfig {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "weekend_surcharge", nullable = false, precision = 6, scale = 4)
    private BigDecimal weekendSurcharge;

    @Column(name = "demand_window_days", nullable = false)
    private int demandWindowDays;

    @Column(name = "demand_baseline", nullable = false, precision = 6, scale = 4)
    private BigDecimal demandBaseline;

    @Column(name = "demand_weight", nullable = false, precision = 6, scale = 4)
    private BigDecimal demandWeight;

    @Column(name = "max_demand_surcharge", nullable = false, precision = 6, scale = 4)
    private BigDecimal maxDemandSurcharge;

    @Column(name = "max_demand_discount", nullable = false, precision = 6, scale = 4)
    private BigDecimal maxDemandDiscount;

    @Column(name = "availability_weight", nullable = false, precision = 6, scale = 4)
    private BigDecimal availabilityWeight;

    @Column(name = "availability_threshold", nullable = false, precision = 6, scale = 4)
    private BigDecimal availabilityThreshold;

    @Column(name = "discount_7_days", nullable = false, precision = 6, scale = 4)
    private BigDecimal discount7Days;

    @Column(name = "discount_14_days", nullable = false, precision = 6, scale = 4)
    private BigDecimal discount14Days;

    @Column(name = "discount_30_days", nullable = false, precision = 6, scale = 4)
    private BigDecimal discount30Days;

    @Column(name = "min_multiplier", nullable = false, precision = 6, scale = 4)
    private BigDecimal minMultiplier;

    @Column(name = "max_multiplier", nullable = false, precision = 6, scale = 4)
    private BigDecimal maxMultiplier;

    @Column(name = "free_cancel_days", nullable = false)
    private int freeCancelDays;

    @Column(name = "late_cancel_fee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal lateCancelFeeRate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 160)
    private String updatedBy = "system";

    /** Built-in defaults identical to the Flyway seed row; used by unit tests and as a safety net. */
    public static PricingConfig defaults() {
        PricingConfig c = new PricingConfig();
        c.weekendSurcharge = new BigDecimal("0.1500");
        c.demandWindowDays = 14;
        c.demandBaseline = new BigDecimal("0.3000");
        c.demandWeight = new BigDecimal("0.5000");
        c.maxDemandSurcharge = new BigDecimal("0.2500");
        c.maxDemandDiscount = new BigDecimal("0.1000");
        c.availabilityWeight = new BigDecimal("0.3000");
        c.availabilityThreshold = new BigDecimal("0.5000");
        c.discount7Days = new BigDecimal("0.0500");
        c.discount14Days = new BigDecimal("0.1000");
        c.discount30Days = new BigDecimal("0.1500");
        c.minMultiplier = new BigDecimal("0.8000");
        c.maxMultiplier = new BigDecimal("1.6000");
        c.freeCancelDays = 2;
        c.lateCancelFeeRate = new BigDecimal("0.2500");
        return c;
    }
}
