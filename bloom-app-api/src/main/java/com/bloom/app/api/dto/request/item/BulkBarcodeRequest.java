package com.bloom.app.api.dto.request.item;

import jakarta.validation.constraints.NotEmpty;
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

    @NotEmpty(message = "SKU list cannot be empty")
    private List<String> skus;
}
