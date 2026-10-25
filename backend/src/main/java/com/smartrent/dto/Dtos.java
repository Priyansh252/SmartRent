package com.smartrent.dto;

import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.EventType;
import com.smartrent.domain.FuelType;
import com.smartrent.domain.NotificationStatus;
import com.smartrent.domain.OutboxStatus;
import com.smartrent.domain.ReportStatus;
import com.smartrent.domain.ReportType;
import com.smartrent.domain.Role;
import com.smartrent.domain.Transmission;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.domain.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/** Request/response records for the REST API. Entities are never serialized directly. */
public final class Dtos {

    private Dtos() {
    }

    // ------------------------------------------------------------------ generic

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record ErrorResponse(Instant timestamp, int status, String error, String code, String message,
                                String path, List<FieldIssue> fieldErrors) {
    }

    public record FieldIssue(String field, String message) {
    }

    // ------------------------------------------------------------------ users & auth

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @Size(max = 30) String phone) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 30) String phone,
            @NotNull Role role) {
    }

    public record SetActiveRequest(@NotNull Boolean active) {
    }

    public record UserDto(Long id, String fullName, String email, String phone, Role role, boolean active,
                          Instant createdAt) {
    }

    public record MeDto(Long id, String fullName, String email, Role role) {
    }

    // ------------------------------------------------------------------ fleet

    public record LocationDto(Long id, String name, String city, String address, double latitude, double longitude) {
    }

    public record VehicleDto(Long id, String registrationNumber, String make, String model, int modelYear,
                             VehicleType vehicleType, FuelType fuelType, Transmission transmission, int seats,
                             BigDecimal dailyRate, VehicleStatus status, LocationDto location, String imageUrl,
                             String description, int mileageKm) {
    }

    public record VehicleRequest(
            @NotBlank @Size(max = 20) String registrationNumber,
            @NotBlank @Size(max = 60) String make,
            @NotBlank @Size(max = 60) String model,
            @Min(1990) @Max(2100) int modelYear,
            @NotNull VehicleType vehicleType,
            @NotNull FuelType fuelType,
            @NotNull Transmission transmission,
            @Min(1) @Max(20) int seats,
            @NotNull @DecimalMin(value = "1.00") @DecimalMax(value = "1000000.00") BigDecimal dailyRate,
            VehicleStatus status,
            @NotNull Long locationId,
            @Size(max = 500) String imageUrl,
            @Size(max = 500) String description,
            @Min(0) int mileageKm) {
    }

    public record StatusUpdateRequest(@NotNull VehicleStatus status) {
    }

    public record MaintenanceRequest(
            @NotBlank @Size(max = 300) String description,
            @NotNull LocalDate performedOn,
            @NotNull @DecimalMin("0.00") BigDecimal cost,
            @Min(0) Integer odometerKm,
            LocalDate nextServiceDate,
            boolean markUnderMaintenance) {
    }

    public record MaintenanceDto(Long id, Long vehicleId, String description, LocalDate performedOn, BigDecimal cost,
                                 Integer odometerKm, LocalDate nextServiceDate, String createdBy, Instant createdAt) {
    }

    public record MetaDto(List<VehicleType> vehicleTypes, List<FuelType> fuelTypes, List<Transmission> transmissions,
                          List<LocationDto> locations, String currency) {
    }

    /** One row of a vehicle search; the estimate fields are only filled when pickup/return dates were supplied. */
    public record VehicleSearchItem(VehicleDto vehicle, BigDecimal estimatedTotal, BigDecimal estimatedDailyAverage) {
    }

    /** Price quote plus a live availability check for the same dates. */
    public record QuoteResponse(PriceBreakdown price, boolean available, String message) {
    }

    /** An already-booked half-open range [pickupDate, returnDate) of a vehicle (no customer data). */
    public record BookedRange(LocalDate pickupDate, LocalDate returnDate) {
    }

    public record DailyRevenue(LocalDate date, BigDecimal revenue, long bookings) {
    }

    public record TypeUtilization(VehicleType type, long vehicles, long rentedVehicleDays,
                                  long availableVehicleDays, double utilizationPercent) {
    }

    public record AdminCharts(List<DailyRevenue> dailyRevenue, List<TypeUtilization> utilizationByType,
                              int windowDays) {
    }

    // ------------------------------------------------------------------ pricing

    public record PriceLine(String code, String label, BigDecimal amount, String explanation) {
    }

    /** Fully explained price. Persisted as JSON on the booking so confirmed prices never change. */
    public record PriceBreakdown(Long vehicleId, LocalDate pickupDate, LocalDate returnDate, int days,
                                 String currency, BigDecimal baseDailyRate, List<PriceLine> lines,
                                 BigDecimal subtotal, BigDecimal rawMultiplier, BigDecimal appliedMultiplier,
                                 boolean multiplierClamped, BigDecimal totalPrice, BigDecimal averageDailyPrice,
                                 Map<String, String> signals) {
    }

    public record PricingConfigDto(
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal weekendSurcharge,
            @Min(1) @Max(365) int demandWindowDays,
            @NotNull @DecimalMin("0") @DecimalMax("5") BigDecimal demandBaseline,
            @NotNull @DecimalMin("0") @DecimalMax("5") BigDecimal demandWeight,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal maxDemandSurcharge,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal maxDemandDiscount,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal availabilityWeight,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal availabilityThreshold,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal discount7Days,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal discount14Days,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal discount30Days,
            @NotNull @DecimalMin("0.1") @DecimalMax("1") BigDecimal minMultiplier,
            @NotNull @DecimalMin("1") @DecimalMax("5") BigDecimal maxMultiplier,
            @Min(0) @Max(60) int freeCancelDays,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal lateCancelFeeRate,
            Instant updatedAt,
            String updatedBy) {
    }

    public record PeakPeriodDto(
            Long id,
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin("0") @DecimalMax("2") BigDecimal surcharge,
            boolean active) {
    }

    // ------------------------------------------------------------------ bookings

    public record BookingRequest(
            @NotNull Long vehicleId,
            @NotNull LocalDate pickupDate,
            @NotNull LocalDate returnDate,
            BigDecimal expectedTotal) {
    }

    public record CancelRequest(@Size(max = 200) String reason) {
    }

    public record CustomerRef(Long id, String fullName, String email) {
    }

    public record BookingDto(Long id, String reference, BookingStatus status, VehicleDto vehicle,
                             CustomerRef customer, LocalDate pickupDate, LocalDate returnDate, int days,
                             BigDecimal dailyBaseRate, BigDecimal totalPrice, PriceBreakdown priceBreakdown,
                             BigDecimal cancellationFee, Instant createdAt, Instant cancelledAt,
                             Instant pickedUpAt, Instant returnedAt) {
    }

    public record TimelineEntry(BookingStatus fromStatus, BookingStatus toStatus, String changedBy, String note,
                                Instant changedAt) {
    }

    // ------------------------------------------------------------------ recommendations

    public record RecommendationRequest(
            @NotNull LocalDate pickupDate,
            @NotNull LocalDate returnDate,
            @DecimalMin("1") BigDecimal maxDailyBudget,
            @Min(1) @Max(20) Integer seats,
            VehicleType type,
            Long locationId,
            boolean sameLocationOnly,
            FuelType preferredFuel,
            Transmission preferredTransmission) {
    }

    public record RecommendationItem(int rank, double score, VehicleDto vehicle, BigDecimal averageDailyPrice,
                                     BigDecimal totalPrice, Double distanceKm, Map<String, Double> componentScores,
                                     List<String> reasons, PriceBreakdown priceBreakdown) {
    }

    public record RecommendationResponse(List<RecommendationItem> recommendations, int candidatesConsidered,
                                         String message, Map<String, Double> weights) {
    }

    // ------------------------------------------------------------------ notifications / events

    public record NotificationDto(Long id, String eventId, EventType eventType, Long bookingId, Long userId,
                                  String title, String message, NotificationStatus status, Instant createdAt,
                                  Instant processedAt) {
    }

    public record OutboxDto(Long id, String eventId, EventType eventType, Long bookingId, OutboxStatus status,
                            int attempts, Instant createdAt, Instant sentAt, String lastError) {
    }

    /** JSON body of every JMS message on the booking-events queue. */
    public record BookingEventMessage(String eventId, EventType eventType, Long bookingId, String bookingReference,
                                      Long userId, Instant occurredAt, String vehicleLabel, LocalDate pickupDate,
                                      LocalDate returnDate, BigDecimal totalPrice, String currency) {
    }

    // ------------------------------------------------------------------ reports, audit, dashboards

    public record RunReportRequest(@NotNull ReportType type, @NotNull LocalDate from, @NotNull LocalDate to) {
    }

    public record ReportRunDto(Long id, ReportType reportType, LocalDate fromDate, LocalDate toDate,
                               ReportStatus status, String triggerSource, Instant startedAt, Instant finishedAt,
                               String fileName, Long rowCount, String errorMessage, boolean downloadable) {
    }

    public record AuditLogDto(Long id, String actor, String action, String entityType, String entityId,
                              String details, Instant createdAt) {
    }

    public record AdminDashboard(long totalVehicles, long bookableVehicles, long vehiclesInMaintenance,
                                 long retiredVehicles, Map<String, Long> bookingsByStatus,
                                 BigDecimal revenueLast30Days, long pickupsLast30Days,
                                 double utilizationLast30DaysPercent, long pendingOutboxEvents,
                                 long failedOutboxEvents, long notificationsProcessed, String currency) {
    }

    public record StaffDashboard(List<BookingDto> pickupsDue, List<BookingDto> activeRentals, long dueCount,
                                 long activeCount) {
    }
}
