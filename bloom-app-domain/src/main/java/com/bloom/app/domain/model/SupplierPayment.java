package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.SupplierPaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Getter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "supplier_payments")
public class SupplierPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false, updatable = false)
    private GoodsReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_session_id", updatable = false)
    private CashSession cashSession;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30, updatable = false)
    private SupplierPaymentMethod paymentMethod;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @Column(name = "reference", length = 255, updatable = false)
    private String reference;

    @Column(name = "note", length = 255, updatable = false)
    private String note;

    @Column(nullable = false, length = 255, updatable = false)
    private String actor;

    @Column(name = "is_voided", nullable = false)
    @Builder.Default
    private boolean voided = false;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by", length = 255)
    private String voidedBy;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public void voidWith(String reason, Instant at, String actor) {
        if (voided) {
            throw new IllegalStateException("Supplier payment is already voided");
        }
        String validatedReason = requireNonBlank(reason, "Void reason");
        Instant validatedAt = Objects.requireNonNull(at, "Void timestamp is required");
        String validatedActor = requireNonBlank(actor, "Void actor");
        voided = true;
        voidReason = validatedReason;
        voidedAt = validatedAt;
        voidedBy = validatedActor;
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
