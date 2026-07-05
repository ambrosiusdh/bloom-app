package com.bloom.app.persistence.repository;

import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.model.DocumentCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentCounterRepository extends JpaRepository<DocumentCounter, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentCounter> findByDocumentTypeAndYearAndMonth(DocumentType documentType, Integer year, Integer month);
}
