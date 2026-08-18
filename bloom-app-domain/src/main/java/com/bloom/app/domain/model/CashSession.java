package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.CashSessionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Table(name = "cash_sessions")
public class CashSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by_id", nullable = false, updatable = false)
    private User openedBy;

    @Column(name = "opening_cash", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal openingCash;

    @Column(name = "expected_closing_cash", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedClosingCash;

    @Column(name = "actual_closing_cash", precision = 19, scale = 4)
    private BigDecimal actualClosingCash;

    @Column(precision = 19, scale = 4)
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CashSessionStatus status = CashSessionStatus.OPEN;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private User closedBy;

    @Version
    private Long version;
}
