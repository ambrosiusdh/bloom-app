package com.bloom.app.domain.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "printer")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PrinterProperties {
    private String printerName;
}
