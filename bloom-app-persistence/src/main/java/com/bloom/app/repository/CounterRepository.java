package com.bloom.app.repository;

import com.bloom.app.domain.model.Counter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CounterRepository extends JpaRepository<Counter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Counter> findByDocumentTypeAndYearAndMonth(String documentType, Integer year, Integer month);
}
