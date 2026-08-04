package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
