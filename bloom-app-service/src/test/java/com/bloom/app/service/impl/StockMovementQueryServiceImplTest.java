package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.model.StockMovement;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.service.mapper.StockMovementMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockMovementQueryServiceImplTest {
    private final StockMovementRepository repository = mock(StockMovementRepository.class);
    private final StockMovementMapper mapper = mock(StockMovementMapper.class);
    private final StockMovementQueryServiceImpl service =
        new StockMovementQueryServiceImpl(repository, mapper);

    @Test
    @SuppressWarnings("unchecked")
    void appliesStableLedgerSortAndMapsTheRepositoryPage() {
        StockMovement movement = StockMovement.builder().id(7L).build();
        StockMovementResponse response = StockMovementResponse.builder().id(7L).build();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(movement), PageRequest.of(0, 10), 1));
        when(mapper.toResponse(movement)).thenReturn(response);

        Page<StockMovementResponse> result = service.filterMovements(
            new FilterStockMovementRequest(), PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactly(response);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void rejectsAnInvertedDateRangeBeforeQuerying() {
        FilterStockMovementRequest request = FilterStockMovementRequest.builder()
            .startDate(Instant.parse("2026-08-12T00:00:00Z"))
            .endDate(Instant.parse("2026-08-11T00:00:00Z"))
            .build();

        assertThatThrownBy(() -> service.filterMovements(request, PageRequest.of(0, 10)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("startDate must not be after endDate");
        verifyNoInteractions(repository, mapper);
    }

    @Test
    void appendsIdTieBreakerAndRejectsUnknownSortProperties() {
        Sort stable = StockMovementQueryServiceImpl.stableSort(
            Sort.by(Sort.Order.asc("createdAt")));

        assertThat(stable.stream().map(Sort.Order::getProperty))
            .containsExactly("createdAt", "id");
        assertThat(stable.getOrderFor("id").isDescending()).isTrue();
        assertThatThrownBy(() -> StockMovementQueryServiceImpl.stableSort(Sort.by("product.category")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported stock movement sort property: product.category");
    }

}
