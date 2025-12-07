package com.bloom.app.service;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.model.*;
import com.bloom.app.repository.StockMovementRepository;
import com.bloom.app.repository.ItemRepository;
import com.bloom.app.repository.ItemAuditLogRepository;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockAdjustmentSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ItemRepository itemRepository;
    private final ItemAuditLogRepository itemAuditLogRepository;

    /**
     * Generic method to record a stock movement.
     * Updates the Item stock quantity and logs to ItemAuditLog.
     */
    @Transactional
    public void recordMovement(MovementSourceType sourceType, Long sourceId, Item item, int quantity,
            MovementType movementType, String createdBy, String referenceNo) {
        log.debug("Recording movement: item={}, qty={}, type={}, source={}", item.getSku(), quantity, movementType,
                sourceType);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        int previousStock = item.getStockQuantity();
        int newStock;

        if (movementType == MovementType.IN) {
            newStock = previousStock + quantity;
        } else {
            newStock = previousStock - quantity;
            if (newStock < 0) {
                // Option: we could throw error if stock goes negative, or allow it.
                // User requirement: "Validate stock does not go negative"
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock for item: " + item.getSku());
            }
        }

        // 1. Update Product Stock (Performance Optimization)
        item.setStockQuantity(newStock);
        itemRepository.save(item);

        // 2. Create StockMovement (Ledger)
        StockMovement movement = StockMovement.builder()
                .product(item)
                .movementType(movementType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .quantity(quantity)
                // CreatedAt/By handled by auditing or we can set if entity listener not fully
                // effective manually
                // but we used @CreatedBy so we let JPA handle it if SecurityContext is set,
                // or we can fallback. For now let's not set manual createdAt unless needed.
                .build();
        stockMovementRepository.save(movement);

        // 3. Create ItemAuditLog (Legacy/Detail view)
        // Mapping MovementSourceType to StockAdjustmentSource might be needed if we
        // want to reuse the exact enum
        // or we can persist raw strings. ItemAuditLog uses StockAdjustmentSource enum.
        // We will try to map loosely or assume compatible.
        // Since MovementSourceType is new, we might need a mapper or just select best
        // fit.
        StockAdjustmentSource auditSource = mapToAuditSource(sourceType);
        StockAdjustmentActionType actionType = (movementType == MovementType.IN) ? StockAdjustmentActionType.ADD
                : StockAdjustmentActionType.REMOVE;

        ItemAuditLog auditLog = ItemAuditLog.builder()
                .item(item)
                .actionType(actionType)
                .qty(quantity)
                .qtyBefore(previousStock)
                .qtyAfter(newStock)
                .source(auditSource)
                .referenceNo(referenceNo)
                .createdBy(createdBy)
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
                    sale.getCreatedBy(), // or current user
                    sale.getCode());
        }
    }

    // Helper for Manual Adjustments
    @Transactional
    public void recordManualAdjustment(StockAdjustment adjustment) {
        for (StockAdjustmentItem item : adjustment.getItems()) {
            MovementType type;
            int qty = item.getChangeQuantity();

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
                if (item.getNewStock() > item.getPreviousStock()) {
                    type = MovementType.IN;
                    qty = item.getNewStock() - item.getPreviousStock();
                } else {
                    type = MovementType.OUT;
                    qty = item.getPreviousStock() - item.getNewStock();
                }
            }

            if (qty > 0) { // Only record if there is a change
                recordMovement(
                        MovementSourceType.STOCK_ADJUSTMENT,
                        adjustment.getId(),
                        item.getItem(),
                        qty,
                        type,
                        adjustment.getCreatedBy(),
                        adjustment.getStockAdjustmentCode());
            }
        }
    }

    private StockAdjustmentSource mapToAuditSource(MovementSourceType type) {
        try {
            return StockAdjustmentSource.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            return StockAdjustmentSource.SYSTEM; // Fallback
        }
    }
}
