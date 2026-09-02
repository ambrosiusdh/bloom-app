package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.PaymentType;
import jakarta.persistence.*;
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
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "sales",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sales_checkout_idempotency_key",
        columnNames = "checkout_idempotency_key"
    )
)
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal changeAmount;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false, updatable = false)
    private CashSession cashSession;

    @Column(name = "checkout_idempotency_key", nullable = false, length = 100, updatable = false)
    private String checkoutIdempotencyKey;

    @Column(name = "checkout_request_hash", nullable = false, length = 64, updatable = false)
    private String checkoutRequestHash;

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

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    private List<SaleItem> items;
}
