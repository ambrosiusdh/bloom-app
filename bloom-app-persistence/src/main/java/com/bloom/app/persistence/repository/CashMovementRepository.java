package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.CashMovement;
import com.bloom.app.persistence.projection.CashMovementTotals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    Page<CashMovement> findBySessionId(Long sessionId, Pageable pageable);

    Optional<CashMovement> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT new com.bloom.app.persistence.projection.CashMovementTotals(
            COALESCE(SUM(CASE
                WHEN movement.direction = com.bloom.app.domain.enums.CashMovementDirection.IN
                THEN movement.amount ELSE 0 END), 0),
            COALESCE(SUM(CASE
                WHEN movement.direction = com.bloom.app.domain.enums.CashMovementDirection.OUT
                THEN movement.amount ELSE 0 END), 0)
        )
        FROM CashMovement movement
        WHERE movement.session.id = :sessionId
        """)
    CashMovementTotals sumAmountsBySession(@Param("sessionId") Long sessionId);

    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))",
        nativeQuery = true
    )
    void lockIdempotencyKey(@Param("key") String key);
}
