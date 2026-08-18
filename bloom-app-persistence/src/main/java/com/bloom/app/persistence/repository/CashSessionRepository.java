package com.bloom.app.persistence.repository;

import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.model.CashSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CashSessionRepository extends JpaRepository<CashSession, Long>, JpaSpecificationExecutor<CashSession> {
    @Query(value = "SELECT pg_advisory_xact_lock(67294367138521)", nativeQuery = true)
    void lockGlobalSessionTransition();

    @EntityGraph(attributePaths = {"openedBy", "closedBy"})
    Optional<CashSession> findFirstByStatus(CashSessionStatus status);

    @Override
    @EntityGraph(attributePaths = {"openedBy", "closedBy"})
    Optional<CashSession> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM CashSession session WHERE session.id = :id")
    Optional<CashSession> findByIdForUpdate(@Param("id") Long id);
}
