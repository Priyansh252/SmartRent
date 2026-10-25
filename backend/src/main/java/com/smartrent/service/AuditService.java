package com.smartrent.service;

import com.smartrent.domain.AuditLog;
import com.smartrent.dto.Dtos.AuditLogDto;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.repo.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records important administrative actions (joins the caller's transaction so the log is atomic with the change). */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void log(String actor, String action, String entityType, Object entityId, String details) {
        repo.save(new AuditLog(actor, action, entityType, entityId == null ? null : String.valueOf(entityId), details));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> list(int page, int size) {
        return PageResponse.of(repo.findAllByOrderByIdDesc(PageRequest.of(Math.max(page, 0), clamp(size)))
                .map(Mappers::toDto));
    }

    static int clamp(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
