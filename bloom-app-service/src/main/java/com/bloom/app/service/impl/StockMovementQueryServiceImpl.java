package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.service.StockMovementQueryService;
import com.bloom.app.service.mapper.StockMovementMapper;
import com.bloom.app.service.specification.StockMovementSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockMovementQueryServiceImpl implements StockMovementQueryService {
    private static final Sort DEFAULT_SORT = Sort.by(
        Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final StockMovementRepository stockMovementRepository;
    private final StockMovementMapper stockMovementMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> filterMovements(
            FilterStockMovementRequest request, Pageable pageable) {
        FilterStockMovementRequest effectiveRequest = request == null
            ? new FilterStockMovementRequest() : request;
        if (effectiveRequest.getStartDate() != null && effectiveRequest.getEndDate() != null
                && effectiveRequest.getStartDate().isAfter(effectiveRequest.getEndDate())) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }

        Pageable effectivePageable = pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return stockMovementRepository.findAll(
                StockMovementSpecification.filter(effectiveRequest), effectivePageable)
            .map(stockMovementMapper::toResponse);
    }
}
