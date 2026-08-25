package com.bloom.app.service.specification;

import com.bloom.app.api.dto.request.supplier.FilterSupplierRequest;
import com.bloom.app.domain.model.Supplier;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SupplierSpecification {
    private SupplierSpecification() {
    }

    public static Specification<Supplier> filter(FilterSupplierRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            boolean active = request.getActive() == null || request.getActive();
            predicates.add(criteriaBuilder.equal(root.get("active"), active));

            String queryText = normalize(request.getQuery());
            if (queryText != null) {
                String pattern = containsPattern(queryText);
                predicates.add(criteriaBuilder.or(
                    like(criteriaBuilder.lower(root.get("code")), pattern, criteriaBuilder),
                    like(criteriaBuilder.lower(root.get("name")), pattern, criteriaBuilder),
                    like(criteriaBuilder.lower(root.get("contactNumber")), pattern, criteriaBuilder),
                    like(criteriaBuilder.lower(root.get("address")), pattern, criteriaBuilder)
                ));
            } else {
                addContains(predicates, criteriaBuilder.lower(root.get("code")), request.getCode(), criteriaBuilder);
                addContains(predicates, criteriaBuilder.lower(root.get("name")), request.getName(), criteriaBuilder);
                addContains(
                    predicates,
                    criteriaBuilder.lower(root.get("contactNumber")),
                    request.getContactNumber(),
                    criteriaBuilder
                );
                addContains(predicates, criteriaBuilder.lower(root.get("address")), request.getAddress(), criteriaBuilder);
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addContains(
        List<Predicate> predicates,
        Expression<String> expression,
        String value,
        jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        String normalized = normalize(value);
        if (normalized != null) {
            predicates.add(like(expression, containsPattern(normalized), criteriaBuilder));
        }
    }

    private static Predicate like(
        Expression<String> expression,
        String pattern,
        jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        return criteriaBuilder.like(expression, pattern, '\\');
    }

    private static String containsPattern(String value) {
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
