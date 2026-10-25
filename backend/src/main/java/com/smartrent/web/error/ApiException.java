package com.smartrent.web.error;

import org.springframework.http.HttpStatus;

/** Base class for expected, client-facing business errors. Mapped to JSON by GlobalExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException vehicleNotFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "Vehicle " + id + " was not found.");
    }

    public static ApiException bookingNotFound(Object idOrRef) {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking " + idOrRef + " was not found.");
    }

    public static ApiException userNotFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User " + id + " was not found.");
    }

    public static ApiException notFound(String what, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " " + id + " was not found.");
    }

    public static ApiException invalidDateRange(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException bookingConflict(Long vehicleId) {
        return new ApiException(HttpStatus.CONFLICT, "BOOKING_CONFLICT",
                "Vehicle " + vehicleId + " is already booked for part of the requested period.");
    }

    public static ApiException vehicleUnavailable(Long vehicleId, String reason) {
        return new ApiException(HttpStatus.CONFLICT, "VEHICLE_UNAVAILABLE",
                "Vehicle " + vehicleId + " is not available: " + reason);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_BOOKING_STATE", message);
    }

    public static ApiException priceChanged(String message) {
        return new ApiException(HttpStatus.CONFLICT, "PRICE_CHANGED", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
