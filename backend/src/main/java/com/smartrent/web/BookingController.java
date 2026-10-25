package com.smartrent.web;

import com.smartrent.domain.BookingStatus;
import com.smartrent.dto.Dtos.BookingDto;
import com.smartrent.dto.Dtos.BookingRequest;
import com.smartrent.dto.Dtos.CancelRequest;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.dto.Dtos.StaffDashboard;
import com.smartrent.dto.Dtos.TimelineEntry;
import com.smartrent.security.AppUserDetails;
import com.smartrent.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    // ---------------------------------------------------------------- customer

    /** Create a booking. Optional Idempotency-Key header makes retries safe (same key + same request = same booking). */
    @PostMapping("/api/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingDto> create(@Valid @RequestBody BookingRequest body,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                             @AuthenticationPrincipal AppUserDetails me) {
        BookingDto created = bookings.create(me.asCurrentUser(), body, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/bookings/" + created.id()).body(created);
    }

    @GetMapping("/api/bookings/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<BookingDto> mine(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @AuthenticationPrincipal AppUserDetails me) {
        return bookings.mine(me.getId(), page, size);
    }

    /** Customers see only their own bookings (403 otherwise); staff can view any. */
    @GetMapping("/api/bookings/{id}")
    public BookingDto get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return bookings.get(id, me.asCurrentUser());
    }

    @GetMapping("/api/bookings/{id}/timeline")
    public List<TimelineEntry> timeline(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return bookings.timeline(id, me.asCurrentUser());
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id, @RequestBody(required = false) @Valid CancelRequest body,
                             @AuthenticationPrincipal AppUserDetails me) {
        return bookings.cancel(id, me.asCurrentUser(), body == null ? null : body.reason());
    }

    // ---------------------------------------------------------------- staff

    @GetMapping("/api/staff/dashboard")
    public StaffDashboard staffDashboard() {
        return bookings.staffDashboard();
    }

    @PostMapping("/api/staff/bookings/{id}/pickup")
    public BookingDto pickup(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return bookings.pickup(id, me.asCurrentUser());
    }

    @PostMapping("/api/staff/bookings/{id}/return")
    public BookingDto returnVehicle(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return bookings.returnVehicle(id, me.asCurrentUser());
    }

    @GetMapping("/api/staff/bookings")
    public PageResponse<BookingDto> staffList(@RequestParam(required = false) BookingStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return bookings.list(status, page, size);
    }

    // ---------------------------------------------------------------- admin

    @GetMapping("/api/admin/bookings")
    public PageResponse<BookingDto> adminList(@RequestParam(required = false) BookingStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return bookings.list(status, page, size);
    }
}
