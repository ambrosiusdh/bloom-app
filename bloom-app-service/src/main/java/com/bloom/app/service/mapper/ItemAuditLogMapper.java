package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.domain.model.ItemAuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { ItemMapper.class })
@Deprecated(forRemoval = false)
public interface ItemAuditLogMapper {
    @Mapping(target = "item", source = "item")
    @Mapping(target = "source", source = "source")
    ItemAuditLogResponse toResponse(ItemAuditLog itemAuditLog);
}
