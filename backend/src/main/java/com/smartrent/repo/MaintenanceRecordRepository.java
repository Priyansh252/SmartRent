package com.smartrent.repo;

import com.smartrent.domain.MaintenanceRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    List<MaintenanceRecord> findByVehicleIdOrderByPerformedOnDescIdDesc(Long vehicleId);
}
