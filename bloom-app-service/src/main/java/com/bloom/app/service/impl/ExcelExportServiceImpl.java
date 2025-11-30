package com.bloom.app.service.impl;

import com.bloom.app.domain.dto.response.sale.SaleResponse;
import com.bloom.app.domain.dto.response.saleitem.SaleItemResponse;
import com.bloom.app.service.ExcelExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class ExcelExportServiceImpl implements ExcelExportService {

    private static final String[] ITEM_HEADERS = {
            "No.", "SKU", "Nama Barang", "Qty", "Harga Unit", "Harga Total"
    };

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy")
            .withZone(ZoneId.systemDefault());

    @Override
    public void exportSaleToExcel(SaleResponse sale, OutputStream outputStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Kwitansi");

            // Styles
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook, dataStyle);
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle rightAlignStyle = createRightAlignStyle(workbook);

            // Red text style for discount
            CellStyle redCurrencyStyle = workbook.createCellStyle();
            redCurrencyStyle.cloneStyleFrom(currencyStyle);
            Font redFont = workbook.createFont();
            redFont.setColor(IndexedColors.RED.getIndex());
            redCurrencyStyle.setFont(redFont);

            int rowIdx = 0;

            // --- Title ---
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(2);
            titleCell.setCellValue("Kwitansi");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5)); // Merge across all columns
            rowIdx++;

            // --- Company & Receipt Info ---
            // Row 2: Company Name (Left) | Date (Right)
            Row infoRow1 = sheet.createRow(rowIdx++);
            createCell(infoRow1, 0, "Bloom App Store", boldStyle);
            createCell(infoRow1, 4, "Date", rightAlignStyle);
            createCell(infoRow1, 5, sale.getCreatedAt() != null ? DATE_FORMATTER.format(sale.getCreatedAt()) : "",
                    rightAlignStyle);

            // Row 3: Address | Receipt #
            Row infoRow2 = sheet.createRow(rowIdx++);
            createCell(infoRow2, 0, "123 Bloom Street", null);
            createCell(infoRow2, 4, "Receipt #", rightAlignStyle);
            createCell(infoRow2, 5, sale.getCode(), rightAlignStyle);

            // Row 4: City/Zip | Cashier
            Row infoRow3 = sheet.createRow(rowIdx++);
            createCell(infoRow3, 0, "Jakarta, 12345", null);
            createCell(infoRow3, 4, "Cashier", rightAlignStyle);
            createCell(infoRow3, 5, sale.getCreatedBy(), rightAlignStyle);

            // Row 5: Phone | Payment Type
            Row infoRow4 = sheet.createRow(rowIdx++);
            createCell(infoRow4, 0, "Phone: (021) 555-0123", null);
            createCell(infoRow4, 4, "Payment Type", rightAlignStyle);
            createCell(infoRow4, 5, sale.getPaymentType() != null ? sale.getPaymentType().name() : "", rightAlignStyle);

            rowIdx += 2; // Spacing

            // --- Items Table Header ---
            Row tableHeaderRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < ITEM_HEADERS.length; i++) {
                Cell cell = tableHeaderRow.createCell(i);
                cell.setCellValue(ITEM_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // --- Items Table Data ---
            int itemNum = 1;
            if (sale.getSaleItems() != null) {
                for (SaleItemResponse item : sale.getSaleItems()) {
                    Row row = sheet.createRow(rowIdx++);
                    createCell(row, 0, itemNum++, dataStyle);
                    createCell(row, 1, item.getItem() != null ? item.getItem().getSku() : "", dataStyle);
                    createCell(row, 2, item.getItem() != null ? item.getItem().getName() : "", dataStyle);
                    createCell(row, 3, item.getQuantity(), dataStyle);
                    createCell(row, 4, item.getUnitPrice(), currencyStyle);
                    createCell(row, 5, item.getSubtotal(), currencyStyle);
                }
            }

            // Fill empty rows
            for (int i = 0; i < 3; i++) {
                Row row = sheet.createRow(rowIdx++);
                for (int j = 0; j < ITEM_HEADERS.length; j++) {
                    createCell(row, j, "", dataStyle);
                }
            }

            // --- Totals Section ---
            // SUBTOTAL
            Row subtotalRow = sheet.createRow(rowIdx++);
            createCell(subtotalRow, 4, "SUBTOTAL", headerStyle);
            createCell(subtotalRow, 5, sale.getSubtotalAmount(), currencyStyle);

            // Diskon
            Row discountRow = sheet.createRow(rowIdx++);
            createCell(discountRow, 4, "Diskon", headerStyle);
            createCell(discountRow, 5, sale.getDiscountAmount(), redCurrencyStyle); // Red for discount

            // Total
            Row totalRow = sheet.createRow(rowIdx++);
            createCell(totalRow, 4, "Total", headerStyle);
            createCell(totalRow, 5, sale.getTotalAmount(), currencyStyle);

            // Dibayar
            Row paidRow = sheet.createRow(rowIdx++);
            createCell(paidRow, 4, "Dibayar", headerStyle);
            createCell(paidRow, 5, sale.getPaidAmount(), currencyStyle);

            // Kembalian (Change)
            BigDecimal change = sale.getPaidAmount().subtract(sale.getTotalAmount());
            if (change.compareTo(BigDecimal.ZERO) >= 0) {
                Row changeRow = sheet.createRow(rowIdx++);
                createCell(changeRow, 4, "Kembalian", headerStyle);
                createCell(changeRow, 5, change, currencyStyle);
            }

            // Auto-size columns
            for (int i = 0; i < ITEM_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);

        } catch (IOException e) {
            log.error("Failed to export sale to Excel", e);
            throw new RuntimeException("Failed to export sale to Excel", e);
        }
    }

    private void createCell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        }
        cell.setCellStyle(style);
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 20);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex()); // Green background
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    private CellStyle createBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createRightAlignStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
}
