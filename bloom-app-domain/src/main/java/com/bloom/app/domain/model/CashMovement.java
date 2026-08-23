package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.CashMovementDirection;
import com.bloom.app.domain.enums.CashMovementSourceType;
import com.bloom.app.domain.enums.CashMovementType;
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
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@Entity
@Immutable
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cash_movements")
public class CashMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false, updatable = false)
    private CashSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 50, updatable = false)
    private CashMovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50, updatable = false)
    private CashMovementSourceType sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "reference_no", nullable = false, length = 100, updatable = false)
    private String referenceNo;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private CashMovementDirection direction;

    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(nullable = false, updatable = false)
    private String actor;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;
}
