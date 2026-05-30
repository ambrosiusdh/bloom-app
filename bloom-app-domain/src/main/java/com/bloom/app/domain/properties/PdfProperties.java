package com.bloom.app.domain.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "pdf")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PdfProperties {
    private int columns = 3;
    private float margin = 36f;
}
