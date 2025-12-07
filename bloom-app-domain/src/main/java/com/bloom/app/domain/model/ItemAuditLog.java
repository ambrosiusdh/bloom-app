package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockAdjustmentSource;
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
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "item_audit_logs")
public class ItemAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private StockAdjustmentActionType actionType;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "qty_before", nullable = false)
    private Integer qtyBefore;

    @Column(name = "qty_after", nullable = false)
    private Integer qtyAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private StockAdjustmentSource source;

    @Column(name = "reference_no")
    private String referenceNo;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;
}
