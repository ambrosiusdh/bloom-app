package com.bloom.app.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "items",
    indexes = {
        @Index(name = "idx_items_category_id", columnList = "item_category_id")
    }
)
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String sku;

    private String description;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit_of_measure", nullable = false, length = 30)
    private UnitOfMeasure baseUnitOfMeasure;

    @Column(name = "fractional_quantity_allowed", nullable = false)
    @Builder.Default
    private boolean fractionalQuantityAllowed = false;

    @Column(name = "stock_store", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal stockStore = BigDecimal.ZERO;

    @Column(name = "stock_warehouse", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal stockWarehouse = BigDecimal.ZERO;

    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_category_id", nullable = false)
    private ItemCategory category;

    @Column(updatable = false)
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    @Column(updatable = false)
    @CreatedBy
    private String createdBy;
    @LastModifiedBy
    private String updatedBy;

    @Version
    private Long version;

    public BigDecimal getTotalStock() {
        BigDecimal store = this.stockStore == null ? BigDecimal.ZERO : this.stockStore;
        BigDecimal warehouse = this.stockWarehouse == null ? BigDecimal.ZERO : this.stockWarehouse;
        return store.add(warehouse);
    }
}
