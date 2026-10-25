package com.smartrent.repo;

import com.smartrent.domain.Vehicle;
import com.smartrent.domain.VehicleStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /**
     * Row-level pessimistic lock (SELECT ... FOR UPDATE). BookingService takes this lock before the overlap
     * check, so concurrent booking attempts for the same vehicle are serialized by the database.
     * No join fetch on purpose: FOR UPDATE would also lock the shared location row and needlessly serialize
     * bookings of different vehicles at the same location.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> lockById(@Param("id") Long id);

    @Query("select v from Vehicle v join fetch v.location where v.id = :id")
    Optional<Vehicle> findWithLocation(@Param("id") Long id);

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);

    long countByStatus(VehicleStatus status);

    @Query("select count(v) from Vehicle v where v.status = com.smartrent.domain.VehicleStatus.AVAILABLE")
    long countBookable();

    @Query("select count(v) from Vehicle v where v.status = com.smartrent.domain.VehicleStatus.AVAILABLE "
            + "and v.vehicleType = :type")
    long countBookableByType(@Param("type") com.smartrent.domain.VehicleType type);

    @Query("select v from Vehicle v join fetch v.location order by v.id")
    List<Vehicle> findAllWithLocation();
}
