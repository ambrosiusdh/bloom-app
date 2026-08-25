package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.api.dto.request.stockadjustment.FilterStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.StockAdjustmentItemRequest;
import com.bloom.app.api.dto.response.stockadjustment.CsvParseResponse;
import com.bloom.app.api.dto.response.stockadjustment.CreateStockAdjustmentResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockAdjustment;
import com.bloom.app.domain.model.StockAdjustmentItem;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockAdjustmentRepository;
import com.bloom.app.service.StockAdjustmentService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.StockAdjustmentMapper;
import com.bloom.app.service.mapper.StockMovementMapper;
import com.bloom.app.service.specification.StockAdjustmentSpecification;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.DataFormatter;
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
import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAdjustmentServiceImpl implements StockAdjustmentService {
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final ItemRepository itemRepository;
    private final StockAdjustmentMapper stockAdjustmentMapper;
    private final StockMovementMapper stockMovementMapper;
    private final StockMovementService stockMovementService;
    private final DocumentCounterServiceImpl documentCounterService;

    @Override
    @Transactional
    public CreateStockAdjustmentResponse createStockAdjustment(CreateStockAdjustmentRequest request) {
        log.debug("StockAdjustmentService createStockAdjustment with request: {}", request);

        StockAdjustment stockAdjustment = stockAdjustmentMapper.createRequestToEntity(request);
        stockAdjustment.setReason(normalizeReason(request.getReason()));
        List<StockAdjustmentItem> stockAdjustmentItems = new ArrayList<>();

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Stock adjustment items are required");
        }

        for (StockAdjustmentItemRequest itemRequest : request.getItems()) {
            if (itemRequest == null) {
                throw new IllegalArgumentException("Stock adjustment item is required");
            }
            if (itemRequest.getStockLocation() == null) {
                throw new IllegalArgumentException("Stock location is required");
            }
            Item item = itemRepository.findItemBySku(itemRequest.getItemSku())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemRequest.getItemSku()));

            BigDecimal previousStock = (itemRequest.getStockLocation() == StockLocation.STORE)
                    ? (item.getStockStore() != null ? item.getStockStore() : BigDecimal.ZERO)
                    : (item.getStockWarehouse() != null ? item.getStockWarehouse() : BigDecimal.ZERO);
            BigDecimal requestedQuantity = itemRequest.getChangeQuantity();
            validateAdjustmentQuantity(
                requestedQuantity, itemRequest.getActionType(), item.isFractionalQuantityAllowed());
            BigDecimal newStock = switch (itemRequest.getActionType()) {
                case ADD -> previousStock.add(requestedQuantity);
                case REMOVE -> previousStock.subtract(requestedQuantity);
                case CORRECTION -> requestedQuantity;
            };
            InventoryQuantityValidator.validateStock(newStock, item.isFractionalQuantityAllowed());
            if (itemRequest.getActionType() == StockAdjustmentActionType.CORRECTION
                    && newStock.compareTo(previousStock) == 0) {
                throw new IllegalArgumentException("Correction target must differ from current stock");
            }

            StockAdjustmentItem stockAdjustmentItem = StockAdjustmentItem.builder()
                    .stockAdjustment(stockAdjustment)
                    .item(item)
                    .actionType(itemRequest.getActionType())
                    .changeQuantity(requestedQuantity)
                    .previousStock(previousStock)
                    .newStock(newStock)
                    .stockLocation(itemRequest.getStockLocation())
                    .build();

            stockAdjustmentItems.add(stockAdjustmentItem);
        }

        stockAdjustment.setItems(stockAdjustmentItems);
        stockAdjustment.setStockAdjustmentCode(documentCounterService.generateNextCode(DocumentType.STOCK_ADJUSTMENT));
        StockAdjustment savedStockAdjustment = stockAdjustmentRepository.save(stockAdjustment);

        // Trigger generic movement logic
        List<StockMovementResponse> movements = stockMovementService
            .recordManualAdjustment(savedStockAdjustment)
            .stream()
            .map(stockMovementMapper::toResponse)
            .toList();

        return CreateStockAdjustmentResponse.builder()
            .adjustment(stockAdjustmentMapper.toResponse(savedStockAdjustment))
            .movements(movements)
            .build();
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
            String normalizedFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            if (normalizedFilename.endsWith(".xls") || normalizedFilename.endsWith(".xlsx")) {
                try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                    Sheet sheet = workbook.getSheetAt(0);
                    DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);
                    for (Row row : sheet) {
                        if (row.getRowNum() == 0)
                            continue; // Skip header
                        if (isBlankRow(row, dataFormatter))
                            continue;
                        int rowNumber = row.getRowNum() + 1;
                        // Expected columns: ItemSku, ChangeQuantity, ActionType, Reason
                        String itemSku = requiredCellText(row, 0, "ItemSku", dataFormatter, rowNumber);
                        String quantityText = requiredCellText(
                            row, 1, "ChangeQuantity", dataFormatter, rowNumber);
                        StockAdjustmentActionType actionType = parseActionType(
                            requiredCellText(row, 2, "ActionType", dataFormatter, rowNumber), rowNumber);
                        BigDecimal changeQuantity = parseAdjustmentQuantity(
                            quantityText, actionType, rowNumber);
                        String reason = optionalCellText(row, 3, dataFormatter);

                        responseList.add(CsvParseResponse.builder()
                                .itemSku(itemSku)
                                .changeQuantity(changeQuantity)
                                .actionType(actionType)
                                .reason(reason)
                                .build());
                    }
                }
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
                    String line;
                    boolean isFirstLine = true;
                    int rowNumber = 0;
                    while ((line = reader.readLine()) != null) {
                        rowNumber++;
                        if (isFirstLine) {
                            isFirstLine = false;
                            continue;
                        }
                        if (line.isBlank())
                            continue;
                        List<String> data = parseCsvLine(line, rowNumber);
                        if (data.size() < 3 || data.size() > 4) {
                            throw new IllegalArgumentException(
                                "Row " + rowNumber + ": expected 3 or 4 columns");
                        }
                        // Expected columns: ItemSku, ChangeQuantity, ActionType, Reason
                        String itemSku = requiredText(data.get(0), "ItemSku", rowNumber);
                        StockAdjustmentActionType actionType = parseActionType(
                            requiredText(data.get(2), "ActionType", rowNumber), rowNumber);
                        BigDecimal changeQuantity = parseAdjustmentQuantity(
                            requiredText(data.get(1), "ChangeQuantity", rowNumber), actionType, rowNumber);
                        String reason = data.size() > 3 && !data.get(3).isBlank()
                            ? data.get(3).trim() : null;

                        responseList.add(CsvParseResponse.builder()
                                .itemSku(itemSku)
                                .changeQuantity(changeQuantity)
                                .actionType(actionType)
                                .reason(reason)
                                .build());
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid CSV/Excel stock adjustment input: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to parse CSV/Excel file", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse file: " + e.getMessage());
        }

        return responseList;
    }

    private void validateAdjustmentQuantity(
            BigDecimal quantity,
            StockAdjustmentActionType actionType,
            boolean fractionalQuantityAllowed) {
        if (actionType == StockAdjustmentActionType.CORRECTION) {
            InventoryQuantityValidator.validateStock(quantity, fractionalQuantityAllowed);
        } else {
            InventoryQuantityValidator.validateIncoming(quantity, fractionalQuantityAllowed);
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }
        return reason.trim();
    }

    private BigDecimal parseAdjustmentQuantity(
            String value, StockAdjustmentActionType actionType, int rowNumber) {
        BigDecimal quantity;
        try {
            quantity = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Row " + rowNumber + ": ChangeQuantity must be a plain decimal number", e);
        }

        try {
            validateAdjustmentQuantity(quantity, actionType, true);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage(), e);
        }
        return quantity;
    }

    private StockAdjustmentActionType parseActionType(String value, int rowNumber) {
        try {
            return StockAdjustmentActionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Row " + rowNumber + ": invalid ActionType '" + value + "'", e);
        }
    }

    private boolean isBlankRow(Row row, DataFormatter dataFormatter) {
        for (int column = 0; column < 4; column++) {
            if (optionalCellText(row, column, dataFormatter) != null) {
                return false;
            }
        }
        return true;
    }

    private String requiredCellText(
            Row row, int column, String field, DataFormatter dataFormatter, int rowNumber) {
        String value = optionalCellText(row, column, dataFormatter);
        return requiredText(value, field, rowNumber);
    }

    private String optionalCellText(Row row, int column, DataFormatter dataFormatter) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        String value = dataFormatter.formatCellValue(cell).trim();
        return value.isBlank() ? null : value;
    }

    private String requiredText(String value, String field, int rowNumber) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Row " + rowNumber + ": " + field + " is required");
        }
        return value.trim();
    }

    private List<String> parseCsvLine(String line, int rowNumber) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Row " + rowNumber + ": unterminated quoted field");
        }
        fields.add(current.toString());
        return fields;
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
