package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.Formula;

import java.time.Instant;
import java.math.BigDecimal;

import com.bloom.app.domain.enums.StockLocation;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_product_id", columnList = "product_id"),
        @Index(name = "idx_stock_movements_source", columnList = "source_type, source_id"),
        @Index(name = "idx_stock_movements_created_at", columnList = "created_at")
})
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Item product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private MovementSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    /**
     * Reference exposed by read APIs. Pre-cutover rows use the verified compatibility snapshot.
     */
    @Formula("""
        coalesce(reference_no, (
            select link.reference_no
            from stock_movement_legacy_audit_links link
            where link.stock_movement_id = id))
        """)
    private String displayReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_action_type")
    private StockAdjustmentActionType adjustmentActionType;

    /**
     * Exact adjustment action, snapshotted during the legacy reconciliation.
     */
    @Enumerated(EnumType.STRING)
    @Formula("""
        case when source_type = 'STOCK_ADJUSTMENT' then
            coalesce(adjustment_action_type, (
                select link.adjustment_action_type
                from stock_movement_legacy_audit_links link
                where link.stock_movement_id = id))
        else null end
        """)
    private StockAdjustmentActionType effectiveAdjustmentActionType;

    @Formula("""
        (select link.item_audit_log_id
         from stock_movement_legacy_audit_links link
         where link.stock_movement_id = id)
        """)
    private Long legacyAuditLogId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    @CreatedBy
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_location", nullable = false)
    private StockLocation stockLocation;

    @Column(name = "qty_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal qtyBefore;

    @Column(name = "qty_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal qtyAfter;
}
