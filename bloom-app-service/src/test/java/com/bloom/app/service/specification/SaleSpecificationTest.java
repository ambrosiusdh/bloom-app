package com.bloom.app.service.specification;

import com.bloom.app.api.dto.request.sale.FilterSaleRequest;
import com.bloom.app.domain.model.Sale;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaleSpecificationTest {
    @SuppressWarnings("unchecked")
    @Test
    void appliesCaseInsensitiveContainsAndInclusiveDateBoundaries() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-31T23:59:59Z");
        FilterSaleRequest request = FilterSaleRequest.builder()
            .code("SaLe-20")
            .createdBy("AdMiN")
            .startDate(start)
            .endDate(end)
            .build();
        Root<Sale> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<String> code = mock(Path.class);
        Path<String> createdBy = mock(Path.class);
        Path<Instant> createdAt = mock(Path.class);
        Expression<String> lowerCode = mock(Expression.class);
        Expression<String> lowerCreatedBy = mock(Expression.class);
        Predicate combined = mock(Predicate.class);

        when(root.<String>get("code")).thenReturn(code);
        when(root.<String>get("createdBy")).thenReturn(createdBy);
        when(root.<Instant>get("createdAt")).thenReturn(createdAt);
        when(builder.lower(code)).thenReturn(lowerCode);
        when(builder.lower(createdBy)).thenReturn(lowerCreatedBy);
        when(builder.and(any(Predicate[].class))).thenReturn(combined);

        Specification<Sale> specification = SaleSpecification.filter(request);
        assertThat(specification.toPredicate(root, query, builder)).isSameAs(combined);

        verify(builder).like(lowerCode, "%sale-20%");
        verify(builder).like(lowerCreatedBy, "%admin%");
        verify(builder).greaterThanOrEqualTo(createdAt, start);
        verify(builder).lessThanOrEqualTo(createdAt, end);
    }
}
