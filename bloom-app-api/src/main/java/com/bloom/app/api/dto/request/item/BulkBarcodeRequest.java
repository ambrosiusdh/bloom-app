package com.bloom.app.api.dto.request.item;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkBarcodeRequest {

    /**
     * List of SKUs for bulk barcode generation.
     * Must not be empty and cannot exceed 100 items per request.
     * Maximum limit is enforced to prevent DOS attacks and resource exhaustion.
     */
    @NotEmpty(message = "SKU list cannot be empty")
    @Size(max = 100, message = "Cannot request more than 100 barcodes at a time")
    private List<String> skus;
}
