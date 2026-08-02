package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.MovementSourceType;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "item_audit_logs")
public class ItemAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal qty;

    @Column(name = "qty_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal qtyBefore;

    @Column(name = "qty_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal qtyAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private MovementSourceType source;

    @Column(name = "reference_no")
    private String referenceNo;

    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;
}
