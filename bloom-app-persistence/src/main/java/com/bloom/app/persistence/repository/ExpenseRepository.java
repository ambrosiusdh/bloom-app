package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.Expense;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtextextended('EXPENSE_CREATE:' || CAST(:key AS text), 0))",
        nativeQuery = true
    )
    void lockCreateIdempotencyKey(@Param("key") String key);

    @EntityGraph(attributePaths = "cashSession")
    Optional<Expense> findByCreateIdempotencyKey(String createIdempotencyKey);

    @Override
    @EntityGraph(attributePaths = "cashSession")
    Optional<Expense> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "cashSession")
    Page<Expense> findAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT expense FROM Expense expense JOIN FETCH expense.cashSession WHERE expense.id = :id")
    Optional<Expense> findByIdForUpdate(@Param("id") Long id);
}
