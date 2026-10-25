package com.smartrent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per status transition: powers the booking status timeline. */
@Entity
@Table(name = "booking_status_history")
@Getter
@Setter
@NoArgsConstructor
public class BookingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private BookingStatus toStatus;

    @Column(name = "changed_by", nullable = false, length = 160)
    private String changedBy;

    @Column(length = 255)
    private String note;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    public BookingStatusHistory(Long bookingId, BookingStatus from, BookingStatus to, String changedBy, String note) {
        this.bookingId = bookingId;
        this.fromStatus = from;
        this.toStatus = to;
        this.changedBy = changedBy;
        this.note = note;
    }
}
