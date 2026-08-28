package com.bloom.app.api.dto.response.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaleCheckoutStatusResponse {
    private Status status;
    private SaleResponse sale;

    public enum Status {
        COMPLETED,
        UNKNOWN
    }
}
