package com.bloom.app.service;

import com.bloom.app.domain.dto.response.sale.SaleResponse;
import java.io.OutputStream;

public interface ExcelExportService {
    void exportSaleToExcel(SaleResponse sale, OutputStream outputStream);
}
