package com.bloom.app.service;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.StockAdjustment;

public interface StockMovementService {
    void recordMovement(
        MovementSourceType sourceType,
        Long sourceId,
        Item item,
        int quantity,
        MovementType movementType,
        String referenceNo
    );
    void recordSaleMovements(Sale sale);
    void recordManualAdjustment(StockAdjustment adjustment);
}
