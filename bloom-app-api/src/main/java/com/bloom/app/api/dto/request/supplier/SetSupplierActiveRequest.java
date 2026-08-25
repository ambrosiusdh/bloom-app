package com.bloom.app.api.dto.request.supplier;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetSupplierActiveRequest {
    @NotNull(message = "Supplier active status is required")
    private Boolean active;
}
