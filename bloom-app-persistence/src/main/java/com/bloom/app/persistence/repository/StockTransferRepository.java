package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.StockTransfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.Instant;

@Repository
public interface StockTransferRepository
        extends JpaRepository<StockTransfer, Long>, JpaSpecificationExecutor<StockTransfer> {
    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:requestKey AS text), 0))",
        nativeQuery = true
    )
    void lockRequestKey(@Param("requestKey") String requestKey);

    @EntityGraph(attributePaths = {"lines", "lines.item"})
    Optional<StockTransfer> findByRequestKey(String requestKey);

    @EntityGraph(attributePaths = {"lines", "lines.item"})
    Optional<StockTransfer> findByCode(String code);

    @Query("SELECT transfer.createdAt FROM StockTransfer transfer WHERE transfer.id = :id")
    Instant findPersistedCreatedAtById(@Param("id") Long id);
}
