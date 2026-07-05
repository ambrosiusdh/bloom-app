package com.bloom.app.service.impl;

import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.RomanMonth;
import com.bloom.app.domain.model.DocumentCounter;
import com.bloom.app.persistence.repository.DocumentCounterRepository;
import com.bloom.app.service.DocumentCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class DocumentCounterServiceImpl implements DocumentCounterService {

    private final DocumentCounterRepository documentCounterRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNextCode(DocumentType documentType) {
        YearMonth currentYearMonth = YearMonth.now();
        int year = currentYearMonth.getYear();
        int month = currentYearMonth.getMonthValue();

        DocumentCounter documentCounter = documentCounterRepository.findByDocumentTypeAndYearAndMonth(documentType, year, month)
                .orElseGet(() -> DocumentCounter.builder()
                        .documentType(documentType)
                        .year(year)
                        .month(month)
                        .currentSequence(0L)
                        .build());

        documentCounter.setCurrentSequence(documentCounter.getCurrentSequence() + 1);
        documentCounterRepository.save(documentCounter);

        String romanMonth = RomanMonth.fromNumber(month);
        return String.format("%s/%s-%d/%04d", documentType.getDocumentPrefix(), romanMonth, year, documentCounter.getCurrentSequence());
    }
}
