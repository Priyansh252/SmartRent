package com.smartrent.repo;

import com.smartrent.domain.OutboxEvent;
import com.smartrent.domain.OutboxStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxStatus status, Pageable pageable);

    Page<OutboxEvent> findAllByOrderByIdDesc(Pageable pageable);

    long countByStatus(OutboxStatus status);
}
