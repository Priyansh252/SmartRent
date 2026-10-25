package com.smartrent.domain;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    CONFIRMED, PICKED_UP, RETURNED, CANCELLED;

    /** Statuses that block a vehicle for the booked interval. */
    public static final Set<BookingStatus> BLOCKING = EnumSet.of(CONFIRMED, PICKED_UP);
}
