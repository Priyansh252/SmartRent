package com.smartrent.repo;

import com.smartrent.domain.PeakPeriod;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeakPeriodRepository extends JpaRepository<PeakPeriod, Long> {

    /** Active periods that intersect the inclusive day range [from, to]. */
    List<PeakPeriod> findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate to, LocalDate from);

    List<PeakPeriod> findAllByOrderByStartDateAsc();
}
