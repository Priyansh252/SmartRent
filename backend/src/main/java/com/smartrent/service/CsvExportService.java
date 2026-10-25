package com.smartrent.service;

import com.smartrent.domain.Booking;
import com.smartrent.domain.Vehicle;
import com.smartrent.repo.BookingRepository;
import com.smartrent.repo.VehicleRepository;
import com.smartrent.util.CsvUtil;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** On-demand CSV exports of fleet and booking data for administrators. */
@Service
public class CsvExportService {

    private final VehicleRepository vehicles;
    private final BookingRepository bookings;

    public CsvExportService(VehicleRepository vehicles, BookingRepository bookings) {
        this.vehicles = vehicles;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public String vehiclesCsv() {
        StringBuilder sb = new StringBuilder("id,registration_number,make,model,year,type,fuel,transmission,seats,daily_rate,status,location,mileage_km\r\n");
        for (Vehicle v : vehicles.findAllWithLocation()) {
            sb.append(CsvUtil.line(List.of(String.valueOf(v.getId()), v.getRegistrationNumber(), v.getMake(), v.getModel(),
                    String.valueOf(v.getModelYear()), v.getVehicleType().name(), v.getFuelType().name(),
                    v.getTransmission().name(), String.valueOf(v.getSeats()), v.getDailyRate().toPlainString(),
                    v.getStatus().name(), v.getLocation().getName(), String.valueOf(v.getMileageKm())))).append("\r\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String bookingsCsv() {
        StringBuilder sb = new StringBuilder("reference,status,customer_email,vehicle,registration_number,pickup_date,return_date,days,total_price,cancellation_fee,created_at\r\n");
        for (Booking b : bookings.findAllByOrderByIdAsc()) {
            sb.append(CsvUtil.line(List.of(b.getReference(), b.getStatus().name(), b.getUser().getEmail(),
                    b.getVehicle().getMake() + " " + b.getVehicle().getModel(), b.getVehicle().getRegistrationNumber(),
                    b.getPickupDate().toString(), b.getReturnDate().toString(),
                    String.valueOf(ChronoUnit.DAYS.between(b.getPickupDate(), b.getReturnDate())),
                    b.getTotalPrice().toPlainString(),
                    b.getCancellationFee() == null ? "" : b.getCancellationFee().toPlainString(),
                    b.getCreatedAt().toString()))).append("\r\n");
        }
        return sb.toString();
    }
}
