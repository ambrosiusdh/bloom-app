package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.StockAdjustmentActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.bloom.app.domain.enums.StockLocation;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stock_adjustment_items")
public class StockAdjustmentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_adjustment_id", nullable = false)
    private StockAdjustment stockAdjustment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private StockAdjustmentActionType actionType;

    @Column(name = "change_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal changeQuantity;

    @Column(name = "previous_stock", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousStock;

    @Column(name = "new_stock", nullable = false, precision = 19, scale = 4)
    private BigDecimal newStock;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_location", nullable = false)
    private StockLocation stockLocation;
}
