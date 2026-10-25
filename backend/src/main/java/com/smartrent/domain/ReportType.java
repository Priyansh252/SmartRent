package com.smartrent.domain;

public enum ReportType {
    REVENUE_SUMMARY, BOOKING_COUNTS, FLEET_AVAILABILITY, VEHICLE_USAGE, FLEET_UTILIZATION;

    /** Spring Batch job name registered for this report. */
    public String jobName() {
        return name().toLowerCase().replace('_', '-') + "-job";
    }
}
