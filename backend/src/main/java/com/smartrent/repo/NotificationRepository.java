package com.smartrent.repo;

import com.smartrent.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByEventId(String eventId);

    Page<Notification> findByUserIdOrderByProcessedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Notification> findAllByOrderByProcessedAtDescIdDesc(Pageable pageable);

    long countByBookingId(Long bookingId);
}
