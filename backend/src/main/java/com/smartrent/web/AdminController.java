package com.smartrent.web;

import com.smartrent.domain.Role;
import com.smartrent.dto.Dtos.AdminCharts;
import com.smartrent.dto.Dtos.AdminDashboard;
import com.smartrent.dto.Dtos.AuditLogDto;
import com.smartrent.dto.Dtos.CreateUserRequest;
import com.smartrent.dto.Dtos.NotificationDto;
import com.smartrent.dto.Dtos.OutboxDto;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.dto.Dtos.PeakPeriodDto;
import com.smartrent.dto.Dtos.PricingConfigDto;
import com.smartrent.dto.Dtos.ReportRunDto;
import com.smartrent.dto.Dtos.RunReportRequest;
import com.smartrent.dto.Dtos.SetActiveRequest;
import com.smartrent.dto.Dtos.UserDto;
import com.smartrent.messaging.NotificationService;
import com.smartrent.security.AppUserDetails;
import com.smartrent.service.AuditService;
import com.smartrent.service.CsvExportService;
import com.smartrent.service.PricingService;
import com.smartrent.service.ReportService;
import com.smartrent.service.StatsService;
import com.smartrent.service.UserService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Everything under /api/admin/** (ADMIN only, enforced in SecurityConfig). Bookings/vehicles admin routes live in their controllers. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService users;
    private final StatsService stats;
    private final PricingService pricing;
    private final ReportService reports;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CsvExportService csv;

    public AdminController(UserService users, StatsService stats, PricingService pricing, ReportService reports,
                           NotificationService notifications, AuditService audit, CsvExportService csv) {
        this.users = users;
        this.stats = stats;
        this.pricing = pricing;
        this.reports = reports;
        this.notifications = notifications;
        this.audit = audit;
        this.csv = csv;
    }

    // ---------------------------------------------------------------- users

    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) Role role,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return users.list(role, page, size);
    }

    @PostMapping("/users")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest body, @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.adminCreate(body, me.asCurrentUser()));
    }

    @PatchMapping("/users/{id}/active")
    public UserDto setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest body,
                             @AuthenticationPrincipal AppUserDetails me) {
        return users.setActive(id, body.active(), me.asCurrentUser());
    }

    // ---------------------------------------------------------------- dashboard & charts

    @GetMapping("/dashboard")
    public AdminDashboard dashboard() {
        return stats.dashboard();
    }

    @GetMapping("/charts")
    public AdminCharts charts(@RequestParam(defaultValue = "30") int days) {
        return stats.charts(days);
    }

    // ---------------------------------------------------------------- pricing configuration

    @GetMapping("/pricing/config")
    public PricingConfigDto pricingConfig() {
        return pricing.getConfigDto();
    }

    @PutMapping("/pricing/config")
    public PricingConfigDto updatePricing(@Valid @RequestBody PricingConfigDto body, @AuthenticationPrincipal AppUserDetails me) {
        return pricing.updateConfig(body, me.getUsername());
    }

    @GetMapping("/pricing/peaks")
    public List<PeakPeriodDto> peaks() {
        return pricing.listPeaks();
    }

    @PostMapping("/pricing/peaks")
    public ResponseEntity<PeakPeriodDto> createPeak(@Valid @RequestBody PeakPeriodDto body, @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricing.savePeak(null, body, me.getUsername()));
    }

    @PutMapping("/pricing/peaks/{id}")
    public PeakPeriodDto updatePeak(@PathVariable Long id, @Valid @RequestBody PeakPeriodDto body,
                                    @AuthenticationPrincipal AppUserDetails me) {
        return pricing.savePeak(id, body, me.getUsername());
    }

    @DeleteMapping("/pricing/peaks/{id}")
    public ResponseEntity<Void> deletePeak(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        pricing.deletePeak(id, me.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- notifications, outbox, audit

    @GetMapping("/notifications")
    public PageResponse<NotificationDto> allNotifications(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return notifications.all(page, size);
    }

    @GetMapping("/outbox")
    public PageResponse<OutboxDto> outbox(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return notifications.outbox(page, size);
    }

    @GetMapping("/audit")
    public PageResponse<AuditLogDto> audit(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return audit.list(page, size);
    }

    // ---------------------------------------------------------------- Spring Batch reports

    /** Runs the report job synchronously and returns the finished run (status, row count, file name). */
    @PostMapping("/reports")
    public ResponseEntity<ReportRunDto> runReport(@Valid @RequestBody RunReportRequest body, @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reports.trigger(body.type(), body.from(), body.to(), me.getUsername()));
    }

    @GetMapping("/reports")
    public PageResponse<ReportRunDto> reportHistory(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return reports.history(page, size);
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource file = reports.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFilename() == null ? "report.csv" : file.getFilename()).build().toString())
                .body(file);
    }

    // ---------------------------------------------------------------- CSV exports

    @GetMapping("/export/vehicles.csv")
    public ResponseEntity<byte[]> exportVehicles() {
        return csvResponse("vehicles.csv", csv.vehiclesCsv());
    }

    @GetMapping("/export/bookings.csv")
    public ResponseEntity<byte[]> exportBookings() {
        return csvResponse("bookings.csv", csv.bookingsCsv());
    }

    private static ResponseEntity<byte[]> csvResponse(String name, String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
