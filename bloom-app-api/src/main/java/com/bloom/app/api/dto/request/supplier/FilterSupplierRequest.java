package com.bloom.app.api.dto.request.supplier;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterSupplierRequest {
    @Size(max = 255, message = "Supplier search must not exceed 255 characters")
    private String query;

    @Size(max = 255, message = "Supplier code filter must not exceed 255 characters")
    private String code;

    @Size(max = 255, message = "Supplier name filter must not exceed 255 characters")
    private String name;

    @Size(max = 255, message = "Supplier contact filter must not exceed 255 characters")
    private String contactNumber;

    @Size(max = 255, message = "Supplier address filter must not exceed 255 characters")
    private String address;

    @Schema(
        description = "Supplier lifecycle state. Omit to list active suppliers; use false to list inactive suppliers.",
        defaultValue = "true"
    )
    private Boolean active;
}
