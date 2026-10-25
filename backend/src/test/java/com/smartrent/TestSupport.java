package com.smartrent;

import com.smartrent.domain.*;
import com.smartrent.repo.LocationRepository;
import com.smartrent.repo.UserRepository;
import com.smartrent.repo.VehicleRepository;
import com.smartrent.security.CurrentUser;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates isolated fixtures (a fresh vehicle per test) on top of the seeded demo data. */
@Component
public class TestSupport {

    private final VehicleRepository vehicles;
    private final LocationRepository locations;
    private final UserRepository users;

    public TestSupport(VehicleRepository vehicles, LocationRepository locations, UserRepository users) {
        this.vehicles = vehicles;
        this.locations = locations;
        this.users = users;
    }

    public Vehicle newVehicle(VehicleType type, int seats, String rate) {
        Vehicle v = new Vehicle();
        v.setRegistrationNumber("T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        v.setMake("Test");
        v.setModel("Car " + seats);
        v.setModelYear(2023);
        v.setVehicleType(type);
        v.setFuelType(FuelType.values()[0]);
        v.setTransmission(Transmission.values()[0]);
        v.setSeats(seats);
        v.setDailyRate(new BigDecimal(rate));
        v.setLocation(locations.findAll().get(0));
        v.setMileageKm(1000);
        return vehicles.saveAndFlush(v);
    }

    public Vehicle newVehicle() {
        return newVehicle(VehicleType.SEDAN, 5, "2000.00");
    }

    public AppUser user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    public CurrentUser actor(String email) {
        AppUser u = user(email);
        return new CurrentUser(u.getId(), u.getEmail(), u.getRole());
    }

    public CurrentUser customer() { return actor("customer@smartrent.demo"); }
    public CurrentUser otherCustomer() { return actor("arjun.mehta@smartrent.demo"); }
    public CurrentUser employee() { return actor("employee@smartrent.demo"); }
    public CurrentUser admin() { return actor("admin@smartrent.demo"); }
}
