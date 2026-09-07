package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.GoodsReceiptStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.EntityListeners;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "goods_receipts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 100, unique = true, nullable = false, updatable = false)
    private String code;

    @Column(name = "received_date", nullable = false, updatable = false)
    private Instant receivedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;

    @Column(name = "supplier_name_snapshot", nullable = false, updatable = false)
    private String supplierNameSnapshot;

    @Column(name = "create_idempotency_key", nullable = false, unique = true, length = 100, updatable = false)
    private String createIdempotencyKey;

    @Column(name = "create_request_hash", nullable = false, length = 64, updatable = false)
    private String createRequestHash;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GoodsReceiptStatus status;

    @Column(name = "description", updatable = false)
    private String description;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<GoodsReceiptItem> items;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    @Version
    private Long version;
}
