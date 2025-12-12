package com.bloom.app.service;

import com.bloom.app.api.dto.request.auditlog.FilterAuditLogRequest;
import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {
    Page<ItemAuditLogResponse> filterAuditLogs(FilterAuditLogRequest request, Pageable pageable);

    Page<ItemAuditLogResponse> getItemAuditLogs(String sku, Pageable pageable);
}
