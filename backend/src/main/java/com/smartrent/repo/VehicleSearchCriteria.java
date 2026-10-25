package com.smartrent.repo;

import com.smartrent.domain.FuelType;
import com.smartrent.domain.Transmission;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.domain.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * All optional vehicle filters. A null field means "do not filter on this".
 * pickupDate/returnDate together activate the database-side availability check over [pickupDate, returnDate).
 */
public record VehicleSearchCriteria(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LocalDate pickupDate,
        LocalDate returnDate,
        VehicleType type,
        Integer minSeats,
        Long locationId,
        FuelType fuelType,
        Transmission transmission,
        VehicleStatus status) {

    public boolean hasDates() {
        return pickupDate != null && returnDate != null;
    }
}
