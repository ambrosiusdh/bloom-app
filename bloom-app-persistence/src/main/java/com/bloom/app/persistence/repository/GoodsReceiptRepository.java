package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.GoodsReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<GoodsReceipt> {
    Optional<GoodsReceipt> findByCode(String code);

    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtextextended('GOODS_RECEIPT_CREATE:' || CAST(:key AS text), 0))",
        nativeQuery = true
    )
    void lockCreateIdempotencyKey(@Param("key") String key);

    @EntityGraph(attributePaths = {"supplier", "items", "items.item", "items.item.category"})
    Optional<GoodsReceipt> findByCreateIdempotencyKey(String createIdempotencyKey);

    @EntityGraph(attributePaths = {"supplier", "items", "items.item", "items.item.category"})
    @Query("SELECT receipt FROM GoodsReceipt receipt WHERE receipt.code = :code")
    Optional<GoodsReceipt> findDetailsByCode(@Param("code") String code);

    @EntityGraph(attributePaths = {"supplier"})
    @Query("SELECT receipt FROM GoodsReceipt receipt WHERE receipt.code = :code")
    Optional<GoodsReceipt> findHeaderByCode(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"supplier", "items", "items.item", "items.item.category"})
    @Query("SELECT DISTINCT receipt FROM GoodsReceipt receipt WHERE receipt.code = :code")
    Optional<GoodsReceipt> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"supplier"})
    @Query("SELECT receipt FROM GoodsReceipt receipt WHERE receipt.code = :code")
    Optional<GoodsReceipt> findPaymentHeaderByCodeForUpdate(@Param("code") String code);

    boolean existsBySupplierId(Long supplierId);

    @Query("""
        SELECT COALESCE(SUM(receipt.totalAmount), 0)
        FROM GoodsReceipt receipt
        WHERE receipt.supplier.id = :supplierId
          AND receipt.status = com.bloom.app.domain.enums.GoodsReceiptStatus.POSTED
        """)
    BigDecimal sumPostedTotalBySupplierId(@Param("supplierId") Long supplierId);

    @Query("SELECT COUNT(g) FROM GoodsReceipt g WHERE g.createdAt BETWEEN :start AND :end")
    long countByCreatedAtBetween(Instant start, Instant end);
}
