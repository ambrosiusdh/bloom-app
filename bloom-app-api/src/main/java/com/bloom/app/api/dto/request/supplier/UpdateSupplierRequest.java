package com.bloom.app.api.dto.request.supplier;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSupplierRequest {
    @Size(max = 255, message = "Supplier name must not exceed 255 characters")
    @Pattern(regexp = ".*\\S.*", message = "Supplier name must not be blank")
    private String name;

    @Size(max = 255, message = "Supplier contact number must not exceed 255 characters")
    private String contactNumber;

    @Size(max = 255, message = "Supplier address must not exceed 255 characters")
    private String address;
}
