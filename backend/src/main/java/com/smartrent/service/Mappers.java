package com.smartrent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrent.domain.AppUser;
import com.smartrent.domain.AuditLog;
import com.smartrent.domain.Booking;
import com.smartrent.domain.BookingStatusHistory;
import com.smartrent.domain.Location;
import com.smartrent.domain.MaintenanceRecord;
import com.smartrent.domain.Notification;
import com.smartrent.domain.OutboxEvent;
import com.smartrent.domain.ReportRun;
import com.smartrent.domain.ReportStatus;
import com.smartrent.domain.Vehicle;
import com.smartrent.dto.Dtos.AuditLogDto;
import com.smartrent.dto.Dtos.BookingDto;
import com.smartrent.dto.Dtos.CustomerRef;
import com.smartrent.dto.Dtos.LocationDto;
import com.smartrent.dto.Dtos.MaintenanceDto;
import com.smartrent.dto.Dtos.NotificationDto;
import com.smartrent.dto.Dtos.OutboxDto;
import com.smartrent.dto.Dtos.PriceBreakdown;
import com.smartrent.dto.Dtos.ReportRunDto;
import com.smartrent.dto.Dtos.TimelineEntry;
import com.smartrent.dto.Dtos.UserDto;
import com.smartrent.dto.Dtos.VehicleDto;
import java.time.temporal.ChronoUnit;

/** Entity -> DTO conversion. Callers must be inside a transaction when lazy associations are not fetched. */
public final class Mappers {

    private Mappers() {
    }

    public static LocationDto toDto(Location l) {
        return new LocationDto(l.getId(), l.getName(), l.getCity(), l.getAddress(), l.getLatitude(), l.getLongitude());
    }

    public static VehicleDto toDto(Vehicle v) {
        return new VehicleDto(v.getId(), v.getRegistrationNumber(), v.getMake(), v.getModel(), v.getModelYear(),
                v.getVehicleType(), v.getFuelType(), v.getTransmission(), v.getSeats(), v.getDailyRate(),
                v.getStatus(), toDto(v.getLocation()), v.getImageUrl(), v.getDescription(), v.getMileageKm());
    }

    public static UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive(),
                u.getCreatedAt());
    }

    public static BookingDto toDto(Booking b, ObjectMapper mapper) {
        PriceBreakdown breakdown = null;
        try {
            breakdown = mapper.readValue(b.getPriceBreakdown(), PriceBreakdown.class);
        } catch (JsonProcessingException e) {
            // snapshot unreadable: still return the booking with its persisted total
        }
        AppUser u = b.getUser();
        int days = (int) ChronoUnit.DAYS.between(b.getPickupDate(), b.getReturnDate());
        return new BookingDto(b.getId(), b.getReference(), b.getStatus(), toDto(b.getVehicle()),
                new CustomerRef(u.getId(), u.getFullName(), u.getEmail()), b.getPickupDate(), b.getReturnDate(), days,
                b.getDailyBaseRate(), b.getTotalPrice(), breakdown, b.getCancellationFee(), b.getCreatedAt(),
                b.getCancelledAt(), b.getPickedUpAt(), b.getReturnedAt());
    }

    public static TimelineEntry toDto(BookingStatusHistory h) {
        return new TimelineEntry(h.getFromStatus(), h.getToStatus(), h.getChangedBy(), h.getNote(), h.getChangedAt());
    }

    public static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getEventId(), n.getEventType(), n.getBookingId(), n.getUserId(),
                n.getTitle(), n.getMessage(), n.getStatus(), n.getCreatedAt(), n.getProcessedAt());
    }

    public static OutboxDto toDto(OutboxEvent e) {
        return new OutboxDto(e.getId(), e.getEventId(), e.getEventType(), e.getBookingId(), e.getStatus(),
                e.getAttempts(), e.getCreatedAt(), e.getSentAt(), e.getLastError());
    }

    public static MaintenanceDto toDto(MaintenanceRecord m) {
        return new MaintenanceDto(m.getId(), m.getVehicle().getId(), m.getDescription(), m.getPerformedOn(),
                m.getCost(), m.getOdometerKm(), m.getNextServiceDate(), m.getCreatedBy(), m.getCreatedAt());
    }

    public static AuditLogDto toDto(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getActor(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getDetails(), a.getCreatedAt());
    }

    public static ReportRunDto toDto(ReportRun r) {
        return new ReportRunDto(r.getId(), r.getReportType(), r.getFromDate(), r.getToDate(), r.getStatus(),
                r.getTriggerSource(), r.getStartedAt(), r.getFinishedAt(), r.getFileName(), r.getRowCount(),
                r.getErrorMessage(), r.getStatus() == ReportStatus.COMPLETED && r.getFilePath() != null);
    }
}
