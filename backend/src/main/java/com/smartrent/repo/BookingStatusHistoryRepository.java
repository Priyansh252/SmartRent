package com.smartrent.repo;

import com.smartrent.domain.BookingStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

    List<BookingStatusHistory> findByBookingIdOrderByChangedAtAscIdAsc(Long bookingId);
}
