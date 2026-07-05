package com.bloom.app.service;

import com.bloom.app.domain.enums.DocumentType;

public interface DocumentCounterService {
    String generateNextCode(DocumentType documentType);
}
