package com.smartrent.repo;

import com.smartrent.domain.Booking;
import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.VehicleType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Number of blocking bookings overlapping the half-open interval [from, to) for one vehicle.
     * Two half-open intervals [a,b) and [c,d) overlap iff a < d AND c < b.
     */
    @Query("select count(b) from Booking b where b.vehicle.id = :vehicleId and b.status in :statuses "
            + "and b.pickupDate < :to and b.returnDate > :from")
    long countOverlapping(@Param("vehicleId") Long vehicleId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          @Param("statuses") Collection<BookingStatus> statuses);

    /** Earliest start of a blocking booking that begins on/after the given date (null if none). */
    @Query("select min(b.pickupDate) from Booking b where b.vehicle.id = :vehicleId "
            + "and b.status in :statuses and b.pickupDate >= :after")
    LocalDate nextPickupAfter(@Param("vehicleId") Long vehicleId,
                              @Param("after") LocalDate after,
                              @Param("statuses") Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.location", "user"})
    Optional<Booking> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.location", "user"})
    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.location", "user"})
    Page<Booking> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.location", "user"})
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.location", "user"})
    Page<Booking> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.location", "user"})
    List<Booking> findByStatusAndPickupDateLessThanEqualOrderByPickupDateAsc(BookingStatus status, LocalDate date);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.location", "user"})
    List<Booking> findByStatusOrderByReturnDateAsc(BookingStatus status);

    /** Demand signal: bookings created inside the look-back window that were not cancelled. */
    @Query("select count(b) from Booking b where b.createdAt >= :since and b.status <> com.smartrent.domain.BookingStatus.CANCELLED")
    long countCreatedSince(@Param("since") Instant since);

    /** Number of distinct bookable vehicles of a type that are blocked during [from, to). */
    @Query("select count(distinct b.vehicle.id) from Booking b where b.vehicle.vehicleType = :type "
            + "and b.vehicle.status = com.smartrent.domain.VehicleStatus.AVAILABLE "
            + "and b.status in :statuses and b.pickupDate < :to and b.returnDate > :from")
    long countBlockedVehiclesOfType(@Param("type") VehicleType type,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    @Param("statuses") Collection<BookingStatus> statuses);

    /** Non-cancelled bookings of a vehicle touching [from, to); used by the utilization report. */
    @Query("select b from Booking b where b.vehicle.id = :vehicleId and b.status <> com.smartrent.domain.BookingStatus.CANCELLED "
            + "and b.pickupDate < :to and b.returnDate > :from")
    List<Booking> findActiveOverlapping(@Param("vehicleId") Long vehicleId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /** Blocking bookings that have not ended yet; shown as "already booked" ranges on the vehicle page. */
    List<Booking> findByVehicleIdAndStatusInAndReturnDateGreaterThanOrderByPickupDateAsc(
            Long vehicleId, Collection<BookingStatus> statuses, LocalDate after);

    long countByStatus(BookingStatus status);

    @Query("select coalesce(sum(b.totalPrice), 0) from Booking b where b.status <> com.smartrent.domain.BookingStatus.CANCELLED "
            + "and b.pickupDate >= :from and b.pickupDate < :to")
    java.math.BigDecimal revenueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(b) from Booking b where b.pickupDate >= :from and b.pickupDate < :to")
    long countPickupsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.location", "user"})
    List<Booking> findAllByOrderByIdAsc();

    /** Row lock used for every state transition (cancel / pickup / return) so they cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);

    /** Non-cancelled bookings touching [from, to) with their vehicle; used by dashboard charts. */
    @Query("select b from Booking b join fetch b.vehicle where b.status <> com.smartrent.domain.BookingStatus.CANCELLED "
            + "and b.pickupDate < :to and b.returnDate > :from")
    List<Booking> findActiveBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
