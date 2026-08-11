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

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockMovementQueryServiceImpl implements StockMovementQueryService {
    private static final Sort DEFAULT_SORT = Sort.by(
        Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Map<String, String> SORT_PROPERTIES = sortProperties();

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

        Pageable effectivePageable = PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(), stableSort(pageable.getSort()));
        return stockMovementRepository.findAll(
                StockMovementSpecification.filter(effectiveRequest), effectivePageable)
            .map(stockMovementMapper::toResponse);
    }

    static Sort stableSort(Sort requestedSort) {
        if (requestedSort.isUnsorted()) {
            return DEFAULT_SORT;
        }

        Sort resolved = Sort.unsorted();
        boolean hasId = false;
        for (Sort.Order order : requestedSort) {
            String entityProperty = SORT_PROPERTIES.get(order.getProperty());
            if (entityProperty == null) {
                throw new IllegalArgumentException(
                    "Unsupported stock movement sort property: " + order.getProperty());
            }
            resolved = resolved.and(Sort.by(new Sort.Order(
                order.getDirection(), entityProperty, order.getNullHandling())));
            hasId |= "id".equals(entityProperty);
        }
        return hasId ? resolved : resolved.and(Sort.by(Sort.Order.desc("id")));
    }

    private static Map<String, String> sortProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("id", "id");
        properties.put("createdAt", "createdAt");
        properties.put("sourceType", "sourceType");
        properties.put("movementType", "movementType");
        properties.put("adjustmentActionType", "effectiveAdjustmentActionType");
        properties.put("location", "stockLocation");
        properties.put("quantity", "quantity");
        properties.put("referenceNo", "displayReference");
        return Map.copyOf(properties);
    }
}
