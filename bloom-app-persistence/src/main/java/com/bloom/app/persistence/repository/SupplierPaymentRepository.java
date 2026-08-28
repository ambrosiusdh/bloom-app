package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.SupplierPayment;
import com.bloom.app.persistence.projection.ReceiptPaymentTotal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtextextended(" +
            "'SUPPLIER_PAYMENT:' || CAST(:key AS text), 0))",
        nativeQuery = true
    )
    void lockIdempotencyKey(@Param("key") String key);

    @EntityGraph(attributePaths = {"receipt", "supplier", "cashSession"})
    Optional<SupplierPayment> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"receipt", "supplier", "cashSession"})
    @Query("SELECT payment FROM SupplierPayment payment WHERE payment.id = :id")
    Optional<SupplierPayment> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"receipt", "supplier", "cashSession"})
    @Query(
        value = "SELECT payment FROM SupplierPayment payment " +
            "WHERE payment.receipt.id = :receiptId",
        countQuery = "SELECT COUNT(payment) FROM SupplierPayment payment " +
            "WHERE payment.receipt.id = :receiptId"
    )
    Page<SupplierPayment> findHistoryByReceiptId(
        @Param("receiptId") Long receiptId, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(payment.amount), 0)
        FROM SupplierPayment payment
        WHERE payment.receipt.id = :receiptId
          AND payment.voided = false
        """)
    BigDecimal sumValidAmountByReceiptId(@Param("receiptId") Long receiptId);

    @Query("""
        SELECT payment.receipt.id AS receiptId,
               SUM(payment.amount) AS paidAmount
        FROM SupplierPayment payment
        WHERE payment.receipt.id IN :receiptIds
          AND payment.voided = false
        GROUP BY payment.receipt.id
        """)
    List<ReceiptPaymentTotal> sumValidAmountsByReceiptIds(
        @Param("receiptIds") Collection<Long> receiptIds);

}
