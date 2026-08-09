package com.bloom.app.domain.model;

import com.bloom.app.domain.enums.StockLocation;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@Immutable
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "stock_transfers")
public class StockTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100, updatable = false)
    private String code;

    @Column(name = "request_key", nullable = false, unique = true, length = 100, updatable = false)
    private String requestKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_location", nullable = false, length = 50, updatable = false)
    private StockLocation sourceLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_location", nullable = false, length = 50, updatable = false)
    private StockLocation destinationLocation;

    @Column(name = "description", updatable = false)
    private String description;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "stockTransfer", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<StockTransferLine> lines;
}
