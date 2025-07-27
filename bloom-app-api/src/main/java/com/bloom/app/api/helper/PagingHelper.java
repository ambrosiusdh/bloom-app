package com.bloom.app.api.helper;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public class PagingHelper {
    public static PageRequest toPageRequest(Pageable pageable) {
        return PageRequest.of(
            Math.max(pageable.getPageNumber() - 1, 0),
            pageable.getPageSize(),
            pageable.getSort()
        );
    }
}
