package com.smartrent.web;

import com.smartrent.domain.FuelType;
import com.smartrent.domain.Transmission;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.domain.VehicleType;
import com.smartrent.dto.Dtos.BookedRange;
import com.smartrent.dto.Dtos.MaintenanceDto;
import com.smartrent.dto.Dtos.MaintenanceRequest;
import com.smartrent.dto.Dtos.MetaDto;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.dto.Dtos.QuoteResponse;
import com.smartrent.dto.Dtos.StatusUpdateRequest;
import com.smartrent.dto.Dtos.VehicleDto;
import com.smartrent.dto.Dtos.VehicleRequest;
import com.smartrent.dto.Dtos.VehicleSearchItem;
import com.smartrent.security.AppUserDetails;
import com.smartrent.service.VehicleService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public vehicle search/detail, staff operational status, admin fleet CRUD. Authorization is set in SecurityConfig. */
@RestController
public class VehicleController {

    private final VehicleService vehicles;

    public VehicleController(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    // ---------------------------------------------------------------- public

    @GetMapping("/api/meta")
    public MetaDto meta() {
        return vehicles.meta();
    }

    /** Dynamic Criteria-API search; every parameter is optional. */
    @GetMapping("/api/vehicles")
    public PageResponse<VehicleSearchItem> search(
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            @RequestParam(required = false) VehicleType type,
            @RequestParam(required = false) Integer minSeats,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) FuelType fuelType,
            @RequestParam(required = false) Transmission transmission,
            @RequestParam(defaultValue = "price") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return vehicles.searchAvailable(minPrice, maxPrice, pickupDate, returnDate, type, minSeats, locationId,
                fuelType, transmission, sort, direction, page, size);
    }

    @GetMapping("/api/vehicles/{id}")
    public VehicleDto get(@PathVariable Long id) {
        return vehicles.get(id);
    }

    @GetMapping("/api/vehicles/{id}/quote")
    public QuoteResponse quote(@PathVariable Long id,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDate,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate) {
        return vehicles.quote(id, pickupDate, returnDate);
    }

    @GetMapping("/api/vehicles/{id}/booked-ranges")
    public List<BookedRange> bookedRanges(@PathVariable Long id) {
        return vehicles.bookedRanges(id);
    }

    // ---------------------------------------------------------------- staff (EMPLOYEE, ADMIN)

    @GetMapping("/api/staff/vehicles")
    public PageResponse<VehicleDto> fleet(@RequestParam(required = false) VehicleStatus status,
                                          @RequestParam(required = false) VehicleType type,
                                          @RequestParam(required = false) Long locationId,
                                          @RequestParam(defaultValue = "make") String sort,
                                          @RequestParam(defaultValue = "asc") String direction,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return vehicles.listFleet(status, type, locationId, sort, direction, page, size);
    }

    @PatchMapping("/api/staff/vehicles/{id}/status")
    public VehicleDto setStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest body,
                                @AuthenticationPrincipal AppUserDetails me) {
        return vehicles.setOperationalStatus(id, body.status(), me.asCurrentUser());
    }

    @GetMapping("/api/staff/vehicles/{id}/maintenance")
    public List<MaintenanceDto> maintenance(@PathVariable Long id) {
        return vehicles.maintenanceHistory(id);
    }

    @PostMapping("/api/staff/vehicles/{id}/maintenance")
    public ResponseEntity<MaintenanceDto> addMaintenance(@PathVariable Long id, @Valid @RequestBody MaintenanceRequest body,
                                                         @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicles.addMaintenance(id, body, me.asCurrentUser()));
    }

    // ---------------------------------------------------------------- admin

    @PostMapping("/api/admin/vehicles")
    public ResponseEntity<VehicleDto> create(@Valid @RequestBody VehicleRequest body, @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicles.create(body, me.asCurrentUser()));
    }

    @PutMapping("/api/admin/vehicles/{id}")
    public VehicleDto update(@PathVariable Long id, @Valid @RequestBody VehicleRequest body,
                             @AuthenticationPrincipal AppUserDetails me) {
        return vehicles.update(id, body, me.asCurrentUser());
    }

    @DeleteMapping("/api/admin/vehicles/{id}")
    public ResponseEntity<Void> retire(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        vehicles.retire(id, me.asCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
