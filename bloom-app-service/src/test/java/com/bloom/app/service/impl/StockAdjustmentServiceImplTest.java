package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.StockAdjustmentItemRequest;
import com.bloom.app.api.dto.response.stockadjustment.CsvParseResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockAdjustment;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockAdjustmentRepository;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.StockAdjustmentMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAdjustmentServiceImplTest {
    private final StockAdjustmentRepository stockAdjustmentRepository = mock(StockAdjustmentRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final StockAdjustmentMapper stockAdjustmentMapper = mock(StockAdjustmentMapper.class);
    private final StockMovementService stockMovementService = mock(StockMovementService.class);
    private final DocumentCounterServiceImpl documentCounterService = mock(DocumentCounterServiceImpl.class);
    private final StockAdjustmentServiceImpl service = new StockAdjustmentServiceImpl(
        stockAdjustmentRepository,
        itemRepository,
        stockAdjustmentMapper,
        stockMovementService,
        documentCounterService
    );

    @Test
    void acceptsZeroAsCorrectionTargetAndStoresSignedDelta() {
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

        when(stockAdjustmentMapper.createRequestToEntity(request)).thenReturn(adjustment);
        when(itemRepository.findItemBySku("ITEM-1")).thenReturn(java.util.Optional.of(item));
        when(documentCounterService.generateNextCode(DocumentType.STOCK_ADJUSTMENT)).thenReturn("SA-1");
        when(stockAdjustmentRepository.save(adjustment)).thenReturn(adjustment);
        when(stockAdjustmentMapper.toResponse(adjustment)).thenReturn(expectedResponse);

        StockAdjustmentResponse response = service.createStockAdjustment(request);

        assertThat(response).isSameAs(expectedResponse);
        assertThat(adjustment.getItems()).hasSize(1);
        assertThat(adjustment.getItems().getFirst().getPreviousStock()).isEqualByComparingTo("5.0000");
        assertThat(adjustment.getItems().getFirst().getNewStock()).isEqualByComparingTo("0.0000");
        assertThat(adjustment.getItems().getFirst().getChangeQuantity()).isEqualByComparingTo("-5.0000");
        verify(stockMovementService).recordManualAdjustment(adjustment);
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
}
