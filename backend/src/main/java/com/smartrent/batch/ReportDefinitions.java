package com.smartrent.batch;

import com.smartrent.domain.ReportType;
import java.time.LocalDate;
import java.util.List;

/**
 * SQL and CSV headers of the five reports. Only portable SQL is used (no vendor date functions) so the same
 * statements run on MySQL in production and on H2 in the tests. Date ranges are half-open: [from, to).
 */
public final class ReportDefinitions {

    /** Which values are bound to the statement's '?' placeholders. */
    public enum Params { RANGE, TODAY_X3, NONE }

    public record Definition(String sql, List<String> headers, Params params) {
    }

    private ReportDefinitions() {
    }

    public static Definition of(ReportType type) {
        return switch (type) {
            case REVENUE_SUMMARY -> new Definition(
                    "SELECT b.pickup_date, COUNT(*), COALESCE(SUM(b.total_price), 0) "
                            + "FROM bookings b WHERE b.status <> 'CANCELLED' AND b.pickup_date >= ? AND b.pickup_date < ? "
                            + "GROUP BY b.pickup_date ORDER BY b.pickup_date",
                    List.of("pickup_date", "bookings", "revenue"), Params.RANGE);
            case BOOKING_COUNTS -> new Definition(
                    "SELECT b.status, COUNT(*), COALESCE(SUM(b.total_price), 0) "
                            + "FROM bookings b WHERE b.pickup_date >= ? AND b.pickup_date < ? "
                            + "GROUP BY b.status ORDER BY b.status",
                    List.of("status", "bookings", "total_value"), Params.RANGE);
            case VEHICLE_USAGE -> new Definition(
                    "SELECT v.registration_number, v.make, v.model, COUNT(b.id), "
                            + "COALESCE(SUM(CASE WHEN b.status = 'RETURNED' THEN 1 ELSE 0 END), 0), "
                            + "COALESCE(SUM(CASE WHEN b.status = 'CANCELLED' THEN 1 ELSE 0 END), 0), "
                            + "COALESCE(SUM(CASE WHEN b.status <> 'CANCELLED' THEN b.total_price ELSE 0 END), 0) "
                            + "FROM vehicles v LEFT JOIN bookings b ON b.vehicle_id = v.id "
                            + "AND b.pickup_date >= ? AND b.pickup_date < ? "
                            + "GROUP BY v.id, v.registration_number, v.make, v.model ORDER BY v.registration_number",
                    List.of("registration_number", "make", "model", "bookings", "completed_rentals", "cancelled", "revenue"),
                    Params.RANGE);
            case FLEET_AVAILABILITY -> new Definition(
                    "SELECT v.registration_number, v.make, v.model, v.vehicle_type, l.name, v.status, "
                            + "CASE WHEN EXISTS (SELECT 1 FROM bookings b WHERE b.vehicle_id = v.id AND b.status = 'PICKED_UP') "
                            + "THEN 'RENTED_OUT' "
                            + "WHEN EXISTS (SELECT 1 FROM bookings b2 WHERE b2.vehicle_id = v.id AND b2.status = 'CONFIRMED' "
                            + "AND b2.pickup_date <= ? AND b2.return_date > ?) THEN 'RESERVED_TODAY' ELSE 'FREE' END, "
                            + "(SELECT MIN(b3.pickup_date) FROM bookings b3 WHERE b3.vehicle_id = v.id "
                            + "AND b3.status = 'CONFIRMED' AND b3.pickup_date >= ?), "
                            + "(SELECT COUNT(*) FROM maintenance_records m WHERE m.vehicle_id = v.id) "
                            + "FROM vehicles v JOIN locations l ON l.id = v.location_id ORDER BY v.registration_number",
                    List.of("registration_number", "make", "model", "vehicle_type", "location", "status",
                            "rental_state_today", "next_confirmed_pickup", "maintenance_records"), Params.TODAY_X3);
            case FLEET_UTILIZATION -> new Definition(
                    "SELECT v.id, v.registration_number, v.vehicle_type, l.name "
                            + "FROM vehicles v JOIN locations l ON l.id = v.location_id "
                            + "WHERE v.status <> 'RETIRED' ORDER BY v.registration_number",
                    List.of("registration_number", "vehicle_type", "location", "booked_days", "period_days",
                            "utilization_percent"), Params.NONE);
        };
    }

    public static Object[] bind(Params p, LocalDate from, LocalDate to, LocalDate today) {
        return switch (p) {
            case RANGE -> new Object[] {java.sql.Date.valueOf(from), java.sql.Date.valueOf(to)};
            case TODAY_X3 -> new Object[] {java.sql.Date.valueOf(today), java.sql.Date.valueOf(today),
                    java.sql.Date.valueOf(today)};
            case NONE -> new Object[0];
        };
    }
}
