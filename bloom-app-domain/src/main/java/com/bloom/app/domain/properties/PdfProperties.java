package com.bloom.app.domain.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "pdf")
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PdfProperties {
    @Min(1)
    @Max(10)
    private int columns = 3;


    @Min(0)
    @Max(100)
    private float margin = 36f;
}
