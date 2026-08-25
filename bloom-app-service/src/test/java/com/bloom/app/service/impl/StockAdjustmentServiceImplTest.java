package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.StockAdjustmentItemRequest;
import com.bloom.app.api.dto.response.stockadjustment.CsvParseResponse;
import com.bloom.app.api.dto.response.stockadjustment.CreateStockAdjustmentResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockAdjustment;
import com.bloom.app.domain.model.StockMovement;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockAdjustmentRepository;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.StockAdjustmentMapper;
import com.bloom.app.service.mapper.StockMovementMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAdjustmentServiceImplTest {
    private final StockAdjustmentRepository stockAdjustmentRepository = mock(StockAdjustmentRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final StockAdjustmentMapper stockAdjustmentMapper = mock(StockAdjustmentMapper.class);
    private final StockMovementMapper stockMovementMapper = mock(StockMovementMapper.class);
    private final StockMovementService stockMovementService = mock(StockMovementService.class);
    private final DocumentCounterServiceImpl documentCounterService = mock(DocumentCounterServiceImpl.class);
    private final StockAdjustmentServiceImpl service = new StockAdjustmentServiceImpl(
        stockAdjustmentRepository,
        itemRepository,
        stockAdjustmentMapper,
        stockMovementMapper,
        stockMovementService,
        documentCounterService
    );

    @Test
    void acceptsZeroAsCorrectionTargetAndPreservesTargetAndBalanceSnapshots() {
        StockAdjustmentItemRequest itemRequest = StockAdjustmentItemRequest.builder()
            .itemSku("ITEM-1")
            .changeQuantity(new BigDecimal("0.0000"))
            .actionType(StockAdjustmentActionType.CORRECTION)
            .stockLocation(StockLocation.STORE)
            .build();
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
            .items(List.of(itemRequest))
            .reason("Physical count")
            .build();
        Item item = Item.builder()
            .sku("ITEM-1")
            .baseUnitOfMeasure(UnitOfMeasure.PIECE)
            .fractionalQuantityAllowed(false)
            .stockStore(new BigDecimal("5.0000"))
            .stockWarehouse(BigDecimal.ZERO)
            .build();
        StockAdjustment adjustment = StockAdjustment.builder().build();
        StockAdjustmentResponse expectedResponse = StockAdjustmentResponse.builder().build();
        StockMovement persistedMovement = StockMovement.builder().id(41L).build();
        StockMovementResponse movementResponse = StockMovementResponse.builder().id(41L).build();

        when(stockAdjustmentMapper.createRequestToEntity(request)).thenReturn(adjustment);
        when(itemRepository.findItemBySku("ITEM-1")).thenReturn(java.util.Optional.of(item));
        when(documentCounterService.generateNextCode(DocumentType.STOCK_ADJUSTMENT)).thenReturn("SA-1");
        when(stockAdjustmentRepository.save(adjustment)).thenReturn(adjustment);
        when(stockMovementService.recordManualAdjustment(adjustment))
            .thenReturn(List.of(persistedMovement));
        when(stockMovementMapper.toResponse(persistedMovement)).thenReturn(movementResponse);
        when(stockAdjustmentMapper.toResponse(adjustment)).thenReturn(expectedResponse);

        CreateStockAdjustmentResponse response = service.createStockAdjustment(request);

        assertThat(response.getAdjustment()).isSameAs(expectedResponse);
        assertThat(response.getMovements()).containsExactly(movementResponse);
        assertThat(adjustment.getItems()).hasSize(1);
        assertThat(adjustment.getItems().getFirst().getPreviousStock()).isEqualByComparingTo("5.0000");
        assertThat(adjustment.getItems().getFirst().getNewStock()).isEqualByComparingTo("0.0000");
        assertThat(adjustment.getItems().getFirst().getChangeQuantity()).isEqualByComparingTo("0.0000");
        verify(stockMovementService).recordManualAdjustment(adjustment);
    }

    @Test
    void definesAddAndRemoveAsPositiveDeltas() {
        StockAdjustmentItemRequest addRequest = StockAdjustmentItemRequest.builder()
            .itemSku("ADD-ITEM")
            .changeQuantity(new BigDecimal("1.2500"))
            .actionType(StockAdjustmentActionType.ADD)
            .stockLocation(StockLocation.STORE)
            .build();
        StockAdjustmentItemRequest removeRequest = StockAdjustmentItemRequest.builder()
            .itemSku("REMOVE-ITEM")
            .changeQuantity(new BigDecimal("0.7500"))
            .actionType(StockAdjustmentActionType.REMOVE)
            .stockLocation(StockLocation.WAREHOUSE)
            .build();
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
            .items(List.of(addRequest, removeRequest))
            .reason("Physical recount")
            .build();
        Item addItem = item("ADD-ITEM", "2.0000", "0.0000");
        Item removeItem = item("REMOVE-ITEM", "0.0000", "2.0000");
        StockAdjustment adjustment = StockAdjustment.builder().build();

        when(stockAdjustmentMapper.createRequestToEntity(request)).thenReturn(adjustment);
        when(itemRepository.findItemBySku("ADD-ITEM")).thenReturn(java.util.Optional.of(addItem));
        when(itemRepository.findItemBySku("REMOVE-ITEM")).thenReturn(java.util.Optional.of(removeItem));
        when(documentCounterService.generateNextCode(DocumentType.STOCK_ADJUSTMENT)).thenReturn("SA-1");
        when(stockAdjustmentRepository.save(adjustment)).thenReturn(adjustment);

        service.createStockAdjustment(request);

        assertThat(adjustment.getItems().getFirst().getPreviousStock()).isEqualByComparingTo("2.0000");
        assertThat(adjustment.getItems().get(0).getNewStock()).isEqualByComparingTo("3.2500");
        assertThat(adjustment.getItems().get(0).getChangeQuantity()).isEqualByComparingTo("1.2500");
        assertThat(adjustment.getItems().get(1).getPreviousStock()).isEqualByComparingTo("2.0000");
        assertThat(adjustment.getItems().get(1).getNewStock()).isEqualByComparingTo("1.2500");
        assertThat(adjustment.getItems().get(1).getChangeQuantity()).isEqualByComparingTo("0.7500");
        verify(stockMovementService).recordManualAdjustment(adjustment);
    }

    @Test
    void rejectsNegativeCorrectionTargetBeforePersistence() {
        StockAdjustmentItemRequest itemRequest = StockAdjustmentItemRequest.builder()
            .itemSku("ITEM-1")
            .changeQuantity(new BigDecimal("-0.0001"))
            .actionType(StockAdjustmentActionType.CORRECTION)
            .stockLocation(StockLocation.STORE)
            .build();
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
            .items(List.of(itemRequest))
            .reason("Physical recount")
            .build();
        StockAdjustment adjustment = StockAdjustment.builder().build();

        when(stockAdjustmentMapper.createRequestToEntity(request)).thenReturn(adjustment);
        when(itemRepository.findItemBySku("ITEM-1"))
            .thenReturn(java.util.Optional.of(item("ITEM-1", "1.0000", "0.0000")));

        assertThatThrownBy(() -> service.createStockAdjustment(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Stock may not be negative");

        verify(stockAdjustmentRepository, never()).save(adjustment);
        verify(stockMovementService, never()).recordManualAdjustment(adjustment);
    }

    @Test
    void rejectsNoOpCorrectionBeforePersistence() {
        StockAdjustmentItemRequest itemRequest = StockAdjustmentItemRequest.builder()
            .itemSku("ITEM-1")
            .changeQuantity(new BigDecimal("1.0000"))
            .actionType(StockAdjustmentActionType.CORRECTION)
            .stockLocation(StockLocation.STORE)
            .build();
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
            .reason("Physical recount")
            .items(List.of(itemRequest))
            .build();
        StockAdjustment adjustment = StockAdjustment.builder().build();

        when(stockAdjustmentMapper.createRequestToEntity(request)).thenReturn(adjustment);
        when(itemRepository.findItemBySku("ITEM-1"))
            .thenReturn(java.util.Optional.of(item("ITEM-1", "1.0000", "0.0000")));

        assertThatThrownBy(() -> service.createStockAdjustment(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Correction target must differ from current stock");

        verify(stockAdjustmentRepository, never()).save(adjustment);
        verify(stockMovementService, never()).recordManualAdjustment(adjustment);
    }

    @Test
    void trimsReasonBeforePersistence() {
        StockAdjustmentItemRequest itemRequest = StockAdjustmentItemRequest.builder()
            .itemSku("ITEM-1")
            .changeQuantity(new BigDecimal("1.0000"))
            .actionType(StockAdjustmentActionType.ADD)
            .stockLocation(StockLocation.STORE)
            .build();
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
            .reason("  Physical recount  ")
            .items(List.of(itemRequest))
            .build();
        StockAdjustment adjustment = StockAdjustment.builder().build();

        when(stockAdjustmentMapper.createRequestToEntity(request)).thenReturn(adjustment);
        when(itemRepository.findItemBySku("ITEM-1"))
            .thenReturn(java.util.Optional.of(item("ITEM-1", "1.0000", "0.0000")));
        when(documentCounterService.generateNextCode(DocumentType.STOCK_ADJUSTMENT)).thenReturn("SA-1");
        when(stockAdjustmentRepository.save(adjustment)).thenReturn(adjustment);
        when(stockMovementService.recordManualAdjustment(adjustment)).thenReturn(List.of());

        service.createStockAdjustment(request);

        assertThat(adjustment.getReason()).isEqualTo("Physical recount");
    }

    @Test
    void parsesZeroCorrectionAndQuotedCsvReason() {
        MockMultipartFile file = csvFile("""
            ItemSku,ChangeQuantity,ActionType,Reason
            ITEM-1,0.0000,CORRECTION,"counted, empty"

            """);

        List<CsvParseResponse> result = service.parseCsv(file);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getChangeQuantity()).isEqualByComparingTo("0.0000");
        assertThat(result.getFirst().getActionType()).isEqualTo(StockAdjustmentActionType.CORRECTION);
        assertThat(result.getFirst().getReason()).isEqualTo("counted, empty");
    }

    @Test
    void rejectsZeroAddWithRowSpecificBadRequest() {
        MockMultipartFile file = csvFile("""
            ItemSku,ChangeQuantity,ActionType,Reason
            ITEM-1,0,ADD,invalid
            """);

        assertThatThrownBy(() -> service.parseCsv(file))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).contains("Row 2: Quantity must be positive");
            });
    }

    @Test
    void reportsBlankQuantityWithRowAndField() {
        MockMultipartFile file = csvFile("""
            ItemSku,ChangeQuantity,ActionType,Reason
            ITEM-1,,REMOVE,invalid
            """);

        assertThatThrownBy(() -> service.parseCsv(file))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getReason()).contains("Row 2: ChangeQuantity is required"));
    }

    @Test
    void skipsBlankSpreadsheetRowsAndAllowsZeroCorrection() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Adjustments");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("ItemSku");
            header.createCell(1).setCellValue("ChangeQuantity");
            header.createCell(2).setCellValue("ActionType");
            header.createCell(3).setCellValue("Reason");
            sheet.createRow(1);
            var data = sheet.createRow(2);
            data.createCell(0).setCellValue("ITEM-1");
            data.createCell(1).setCellValue("0.0000");
            data.createCell(2).setCellValue("CORRECTION");
            workbook.write(outputStream);
            workbookBytes = outputStream.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
            "file", "adjustments.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        List<CsvParseResponse> result = service.parseCsv(file);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getChangeQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getFirst().getReason()).isNull();
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
            "file", "adjustments.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private Item item(String sku, String storeStock, String warehouseStock) {
        return Item.builder()
            .id((long) sku.hashCode())
            .sku(sku)
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(true)
            .stockStore(new BigDecimal(storeStock))
            .stockWarehouse(new BigDecimal(warehouseStock))
            .build();
    }
}
