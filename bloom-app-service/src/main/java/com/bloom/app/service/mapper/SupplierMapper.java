package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.request.supplier.CreateSupplierRequest;
import com.bloom.app.api.dto.request.supplier.UpdateSupplierRequest;
import com.bloom.app.api.dto.response.supplier.SupplierResponse;
import com.bloom.app.domain.model.Supplier;
import com.bloom.app.domain.validation.SupplierCodePolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SupplierMapper {
    SupplierResponse entityToResponse(Supplier supplier);

    @Mapping(target = "active", constant = "true")
    @Mapping(target = "code", qualifiedByName = "supplierCode")
    @Mapping(target = "name", qualifiedByName = "requiredText")
    @Mapping(target = "contactNumber", qualifiedByName = "optionalText")
    @Mapping(target = "address", qualifiedByName = "optionalText")
    Supplier createRequestToEntity(CreateSupplierRequest request);

    @Mapping(target = "code", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "name", qualifiedByName = "requiredText")
    @Mapping(target = "contactNumber", qualifiedByName = "optionalText")
    @Mapping(target = "address", qualifiedByName = "optionalText")
    void copyUpdateRequestToEntity(UpdateSupplierRequest request, @MappingTarget Supplier supplier);

    @Named("supplierCode")
    static String normalizeCode(String code) {
        return SupplierCodePolicy.normalize(code);
    }

    @Named("requiredText")
    static String normalizeRequired(String value) {
        return value.trim();
    }

    @Named("optionalText")
    static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
