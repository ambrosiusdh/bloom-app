package com.bloom.app.service.impl;

import com.bloom.app.domain.dto.request.auditlog.FilterAuditLogRequest;
import com.bloom.app.domain.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.domain.model.ItemAuditLog;
import com.bloom.app.repository.ItemAuditLogRepository;
import com.bloom.app.service.AuditLogService;
import com.bloom.app.service.mapper.ItemAuditLogMapper;
import com.bloom.app.service.specification.ItemAuditLogSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {
    private final ItemAuditLogRepository itemAuditLogRepository;
    private final ItemAuditLogMapper itemAuditLogMapper;

    @Override
    public Page<ItemAuditLogResponse> filterAuditLogs(FilterAuditLogRequest request, Pageable pageable) {
        log.debug("AuditLogService filterAuditLogs with request: {}", request);
        Page<ItemAuditLog> auditLogPage = itemAuditLogRepository.findAll(ItemAuditLogSpecification.filter(request),
                pageable);

        List<ItemAuditLogResponse> responseList = auditLogPage.getContent()
                .stream()
                .map(itemAuditLogMapper::toResponse)
                .toList();

        return new PageImpl<>(responseList, pageable, auditLogPage.getTotalElements());
    }

    @Override
    public Page<ItemAuditLogResponse> getItemAuditLogs(String sku, Pageable pageable) {
        log.debug("AuditLogService getItemAuditLogs with sku: {}", sku);
        // Assuming we want to filter by SKU, we can update the repository or
        // specification.
        // Or we can find the item first.
        // Let's use specification for flexibility or add a method in repo.
        // Since ItemAuditLog has Item, and Item has SKU.
        // Let's update repository to find by Item_Sku.
        return itemAuditLogRepository.findByItemSku(sku, pageable)
                .map(itemAuditLogMapper::toResponse);
    }
}
