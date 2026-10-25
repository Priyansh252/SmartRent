package com.smartrent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A configurable seasonal/peak window. Both startDate and endDate are inclusive calendar days. */
@Entity
@Table(name = "peak_periods")
@Getter
@Setter
@NoArgsConstructor
public class PeakPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal surcharge;

    @Column(nullable = false)
    private boolean active = true;

    public PeakPeriod(String name, LocalDate startDate, LocalDate endDate, BigDecimal surcharge) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.surcharge = surcharge;
    }

    public boolean covers(LocalDate day) {
        return active && !day.isBefore(startDate) && !day.isAfter(endDate);
    }
}
