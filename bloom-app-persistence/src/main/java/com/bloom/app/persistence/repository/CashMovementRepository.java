package com.bloom.app.persistence.repository;

import com.bloom.app.domain.enums.CashMovementDirection;
import com.bloom.app.domain.model.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    List<CashMovement> findBySessionIdOrderByOccurredAtAscIdAsc(Long sessionId);

    Optional<CashMovement> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT COALESCE(SUM(movement.amount), 0)
        FROM CashMovement movement
        WHERE movement.session.id = :sessionId
          AND movement.direction = :direction
        """)
    BigDecimal sumAmountBySessionAndDirection(
        @Param("sessionId") Long sessionId,
        @Param("direction") CashMovementDirection direction
    );

    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))",
        nativeQuery = true
    )
    void lockIdempotencyKey(@Param("key") String key);
}
