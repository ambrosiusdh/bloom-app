package com.bloom.app.api.dto.request.supplier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSupplierRequest {
    @NotBlank(message = "Supplier name is required")
    @Size(max = 255, message = "Supplier name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Supplier code is required")
    @Size(max = 255, message = "Supplier code must not exceed 255 characters")
    private String code;

    @Size(max = 255, message = "Supplier contact number must not exceed 255 characters")
    private String contactNumber;

    @Size(max = 255, message = "Supplier address must not exceed 255 characters")
    private String address;
}
