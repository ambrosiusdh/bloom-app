package com.bloom.app.api.dto.request.goodsreceipt;

import com.bloom.app.domain.validation.SupplierCodePolicy;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterGoodsReceiptRequest {
    private String code;

    @Size(
        max = SupplierCodePolicy.MAX_LENGTH,
        message = "Supplier code filter must not exceed "
            + SupplierCodePolicy.MAX_LENGTH + " characters"
    )
    private String supplierCode;

    private String supplierName;
    private LocalDate receivedDateFrom;
    private LocalDate receivedDateTo;
}
