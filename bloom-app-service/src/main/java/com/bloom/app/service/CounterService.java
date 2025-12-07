package com.bloom.app.service;

import com.bloom.app.domain.enums.RomanMonth;
import com.bloom.app.domain.model.Counter;
import com.bloom.app.repository.CounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class CounterService {

    private final CounterRepository counterRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNextCode(String documentType, String prefix) {
        YearMonth currentYearMonth = YearMonth.now();
        int year = currentYearMonth.getYear();
        int month = currentYearMonth.getMonthValue();

        Counter counter = counterRepository.findByDocumentTypeAndYearAndMonth(documentType, year, month)
                .orElseGet(() -> Counter.builder()
                        .documentType(documentType)
                        .year(year)
                        .month(month)
                        .currentSequence(0L)
                        .build());

        counter.setCurrentSequence(counter.getCurrentSequence() + 1);
        counterRepository.save(counter);

        String romanMonth = RomanMonth.fromNumber(month);
        return String.format("%s/%s-%d/%04d", prefix, romanMonth, year, counter.getCurrentSequence());
    }
}
