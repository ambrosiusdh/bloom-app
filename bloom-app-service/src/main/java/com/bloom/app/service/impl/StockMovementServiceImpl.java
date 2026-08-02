package com.bloom.app.service.impl;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.*;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.ItemAuditLogRepository;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementServiceImpl implements StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ItemRepository itemRepository;
    private final ItemAuditLogRepository itemAuditLogRepository;

    /**
     * Generic method to record a stock movement.
     * Updates the Item stock quantity and logs to ItemAuditLog.
     */
    @Transactional
    public void recordMovement(
        MovementSourceType sourceType,
        Long sourceId,
        Item item,
        BigDecimal quantity,
        MovementType movementType,
        String referenceNo,
        StockLocation stockLocation
    ) {
        log.debug("Recording movement: item={}, qty={}, type={}, source={}, location={}", item.getSku(), quantity, movementType,
                sourceType, stockLocation);

        InventoryQuantityValidator.validateIncoming(quantity, item.isFractionalQuantityAllowed());

        BigDecimal previousStock = (stockLocation == StockLocation.STORE)
                ? (item.getStockStore() != null ? item.getStockStore() : BigDecimal.ZERO)
                : (item.getStockWarehouse() != null ? item.getStockWarehouse() : BigDecimal.ZERO);
        BigDecimal newStock;

        if (movementType == MovementType.IN) {
            newStock = previousStock.add(quantity);
        } else {
            newStock = previousStock.subtract(quantity);
            if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock in " + stockLocation + " for item: " + item.getSku());
            }
        }
        InventoryQuantityValidator.validateStock(newStock, item.isFractionalQuantityAllowed());

        // 1. Update Product Stock (Performance Optimization)
        if (stockLocation == StockLocation.STORE) {
            item.setStockStore(newStock);
        } else {
            item.setStockWarehouse(newStock);
        }
        itemRepository.save(item);

        // 2. Create StockMovement (Ledger)
        StockMovement movement = StockMovement.builder()
                .product(item)
                .movementType(movementType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .quantity(quantity)
                .stockLocation(stockLocation)
                .qtyBefore(previousStock)
                .qtyAfter(newStock)
                .build();
        stockMovementRepository.save(movement);

        ItemAuditLog auditLog = ItemAuditLog.builder()
                .item(item)
                .qty(quantity)
                .qtyBefore(previousStock)
                .qtyAfter(newStock)
                .source(sourceType)
                .referenceNo(referenceNo)
                .build();
        itemAuditLogRepository.save(auditLog);
    }

    // Helper to record sales
    @Transactional
    public void recordSaleMovements(Sale sale) {
        for (SaleItem saleItem : sale.getItems()) {
            recordMovement(
                    MovementSourceType.SALE,
                    sale.getId(),
                    saleItem.getItem(),
                    saleItem.getQuantity(),
                    MovementType.OUT,
                    sale.getCode(),
                    saleItem.getStockLocation()
            );
        }
    }

    // Helper for Manual Adjustments
    @Transactional
    public void recordManualAdjustment(StockAdjustment adjustment) {
        for (StockAdjustmentItem item : adjustment.getItems()) {
            MovementType type;
            BigDecimal qty = item.getChangeQuantity();

            // Logic to determine IN/OUT based on ActionType
            // ADD -> IN
            // REMOVE -> OUT
            // CORRECTION -> Calculated delta.
            // NOTE: The StockAdjustmentService already calculated delta logic before
            // calling this?
            // Or we move that logic here?
            // User said: "StockAdjustment will generate StockMovement entries"
            // The StockAdjustmentServiceImpl calculates 'changeQuantity' and 'newStock'.
            // We should use the calculated changeQuantity.

            if (item.getActionType() == StockAdjustmentActionType.ADD) {
                type = MovementType.IN;
            } else if (item.getActionType() == StockAdjustmentActionType.REMOVE) {
                type = MovementType.OUT;
            } else {
                // CORRECTION: Since we don't store 'delta' directly in StockAdjustmentItem
                // cleanly for IN/OUT
                // (it stores changeQuantity and newStock), we need to derive.
                // If previous < new, it's IN.
                // If previous > new, it's OUT.
                if (item.getNewStock().compareTo(item.getPreviousStock()) > 0) {
                    type = MovementType.IN;
                    qty = item.getNewStock().subtract(item.getPreviousStock());
                } else {
                    type = MovementType.OUT;
                    qty = item.getPreviousStock().subtract(item.getNewStock());
                }
            }

            if (qty.compareTo(BigDecimal.ZERO) > 0) { // Only record if there is a change
                recordMovement(
                    MovementSourceType.STOCK_ADJUSTMENT,
                    adjustment.getId(),
                    item.getItem(),
                    qty,
                    type,
                    adjustment.getStockAdjustmentCode(),
                    item.getStockLocation()
                );
            }
        }
    }
}
