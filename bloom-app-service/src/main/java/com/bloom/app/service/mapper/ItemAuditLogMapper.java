package com.bloom.app.service.mapper;

import com.bloom.app.domain.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.domain.model.ItemAuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { ItemMapper.class })
public interface ItemAuditLogMapper {
    @Mapping(target = "item", source = "item")
    ItemAuditLogResponse toResponse(ItemAuditLog itemAuditLog);
}
