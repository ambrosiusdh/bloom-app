package com.bloom.app.service;

import com.bloom.app.api.dto.response.sale.SaleResponse;
import java.io.OutputStream;

public interface ExcelExportService {
    void exportSaleToExcel(SaleResponse sale, OutputStream outputStream);
}
