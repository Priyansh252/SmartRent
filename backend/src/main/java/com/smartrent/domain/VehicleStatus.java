package com.smartrent.domain;

/** Operational status of a vehicle. "Currently rented" is derived from an active PICKED_UP booking. */
public enum VehicleStatus {
    AVAILABLE, MAINTENANCE, RETIRED
}
