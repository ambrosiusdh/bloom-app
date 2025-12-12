package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.FilterStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.StockAdjustmentItemRequest;
import com.bloom.app.api.dto.response.stockadjustment.CsvParseResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockAdjustment;
import com.bloom.app.domain.model.StockAdjustmentItem;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockAdjustmentRepository;
import com.bloom.app.service.StockAdjustmentService;
import com.bloom.app.service.mapper.StockAdjustmentMapper;
import com.bloom.app.service.specification.StockAdjustmentSpecification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAdjustmentServiceImpl implements StockAdjustmentService {
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final ItemRepository itemRepository;
    private final StockAdjustmentMapper stockAdjustmentMapper;
    private final StockMovementServiceImpl stockMovementService;
    private final DocumentCounterServiceImpl documentCounterService;

    @Override
    @Transactional
    public StockAdjustmentResponse createStockAdjustment(CreateStockAdjustmentRequest request) {
        log.debug("StockAdjustmentService createStockAdjustment with request: {}", request);

        StockAdjustment stockAdjustment = stockAdjustmentMapper.createRequestToEntity(request);
        stockAdjustment.setStockAdjustmentCode(documentCounterService.generateNextCode("STOCK_ADJUSTMENT", "SA"));

        List<StockAdjustmentItem> stockAdjustmentItems = new ArrayList<>();

        for (StockAdjustmentItemRequest itemRequest : request.getItems()) {
            Item item = itemRepository.findItemBySku(itemRequest.getItemSku())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Item not found: " + itemRequest.getItemSku()));

            int previousStock = item.getStockQuantity();
            int changeQuantity = itemRequest.getChangeQuantity();
            int newStock;

            if (itemRequest.getActionType() == StockAdjustmentActionType.ADD) {
                newStock = previousStock + changeQuantity;
            } else if (itemRequest.getActionType() == StockAdjustmentActionType.REMOVE) {
                newStock = previousStock - changeQuantity;
            } else if (itemRequest.getActionType() == StockAdjustmentActionType.CORRECTION) {
                newStock = changeQuantity; // Correction sets the absolute value
                changeQuantity = newStock - previousStock; // Calculate delta
            } else {
                newStock = previousStock;
            }

            StockAdjustmentItem stockAdjustmentItem = StockAdjustmentItem.builder()
                    .stockAdjustment(stockAdjustment)
                    .item(item)
                    .actionType(itemRequest.getActionType())
                    .changeQuantity(changeQuantity)
                    .previousStock(previousStock)
                    .newStock(newStock)
                    .build();

            stockAdjustmentItems.add(stockAdjustmentItem);
        }

        stockAdjustment.setItems(stockAdjustmentItems);
        StockAdjustment savedStockAdjustment = stockAdjustmentRepository.save(stockAdjustment);

        // Trigger generic movement logic
        stockMovementService.recordManualAdjustment(savedStockAdjustment);

        return stockAdjustmentMapper.toResponse(savedStockAdjustment);
    }

    @Override
    public Page<StockAdjustmentResponse> filterStockAdjustments(FilterStockAdjustmentRequest request,
            Pageable pageable) {
        log.debug("StockAdjustmentService filterStockAdjustments with request: {}", request);
        Page<StockAdjustment> stockAdjustmentPage = stockAdjustmentRepository
                .findAll(StockAdjustmentSpecification.filter(request), pageable);

        List<StockAdjustmentResponse> responseList = stockAdjustmentPage.getContent()
                .stream()
                .map(stockAdjustmentMapper::toResponse)
                .toList();

        return new PageImpl<>(responseList, pageable, stockAdjustmentPage.getTotalElements());
    }

    @Override
    public StockAdjustmentResponse getStockAdjustmentDetails(String code) {
        log.debug("StockAdjustmentService getStockAdjustmentDetails with code: {}", code);
        return stockAdjustmentRepository.findByStockAdjustmentCode(code)
                .map(stockAdjustmentMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock Adjustment not found"));
    }

    @Override
    public List<CsvParseResponse> parseCsv(MultipartFile file) {
        log.debug("StockAdjustmentService parseCsv");
        List<CsvParseResponse> responseList = new ArrayList<>();

        try {
            String filename = file.getOriginalFilename();
            if (filename != null && (filename.endsWith(".xls") || filename.endsWith(".xlsx"))) {
                try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                    Sheet sheet = workbook.getSheetAt(0);
                    for (Row row : sheet) {
                        if (row.getRowNum() == 0)
                            continue; // Skip header
                        // Expected columns: ItemSku, ChangeQuantity, ActionType, Reason
                        String itemSku = row.getCell(0).getStringCellValue();
                        Integer changeQuantity = (int) row.getCell(1).getNumericCellValue();
                        String actionTypeStr = row.getCell(2).getStringCellValue();
                        String reason = row.getCell(3).getStringCellValue();

                        responseList.add(CsvParseResponse.builder()
                                .itemSku(itemSku)
                                .changeQuantity(changeQuantity)
                                .actionType(StockAdjustmentActionType.valueOf(actionTypeStr))
                                .reason(reason)
                                .build());
                    }
                }
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
                    String line;
                    boolean isFirstLine = true;
                    while ((line = reader.readLine()) != null) {
                        if (isFirstLine) {
                            isFirstLine = false;
                            continue;
                        }
                        String[] data = line.split(",");
                        // Expected columns: ItemSku, ChangeQuantity, ActionType, Reason
                        String itemSku = data[0];
                        Integer changeQuantity = Integer.parseInt(data[1]);
                        String actionTypeStr = data[2];
                        String reason = data.length > 3 ? data[3] : null;

                        responseList.add(CsvParseResponse.builder()
                                .itemSku(itemSku)
                                .changeQuantity(changeQuantity)
                                .actionType(StockAdjustmentActionType.valueOf(actionTypeStr))
                                .reason(reason)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse CSV/Excel file", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse file: " + e.getMessage());
        }

        return responseList;
    }

    @Override
    public void downloadTemplate(OutputStream outputStream) {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Stock Adjustment");

            // Create Header
            Row headerRow = sheet.createRow(0);
            String[] columns = { "ItemSku", "ChangeQuantity", "ActionType", "Reason" };
            for (int i = 0; i < columns.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
            }

            // Add Data Validation for ActionType (Column 2)
            org.apache.poi.ss.usermodel.DataValidationHelper validationHelper = sheet.getDataValidationHelper();
            List<String> actionTypes = new ArrayList<>();
            for (StockAdjustmentActionType type : StockAdjustmentActionType.values()) {
                actionTypes.add(type.name());
            }
            org.apache.poi.ss.usermodel.DataValidationConstraint constraint = validationHelper
                    .createExplicitListConstraint(actionTypes.toArray(new String[0]));
            org.apache.poi.ss.util.CellRangeAddressList addressList = new org.apache.poi.ss.util.CellRangeAddressList(1,
                    1000, 2, 2);
            org.apache.poi.ss.usermodel.DataValidation validation = validationHelper.createValidation(constraint,
                    addressList);
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
        } catch (IOException e) {
            log.error("Failed to generate Excel template", e);
            throw new RuntimeException("Failed to generate Excel template", e);
        }
    }
}
